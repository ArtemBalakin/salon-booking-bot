(function () {
  "use strict";

  var tg = window.Telegram && window.Telegram.WebApp ? window.Telegram.WebApp : null;
  var state = {
    step: 1, services: [], masters: [], selectedService: null, selectedMaster: null,
    selectedDate: null, slots: [], selectedSlot: null, slotsRequestId: 0, mastersRequestId: 0,
    sending: false, done: false
  };
  var $ = function (id) { return document.getElementById(id); };
  var el = {
    title: $("stepTitle"), description: $("stepDescription"), eyebrow: $("sectionEyebrow"),
    number: $("stepNumber"), hint: $("stepHint"), back: $("backBtn"), action: $("mainActionBtn"),
    dots: document.querySelectorAll(".dot"), services: $("servicesList"), masters: $("mastersList"),
    days: $("daysRow"), slots: $("slotsGrid"), noSlots: $("noSlots"),
    name: $("clientName"), phone: $("clientPhone"),
    sumService: $("sumService"), sumMaster: $("sumMaster"), sumTime: $("sumTime"), sumPrice: $("sumPrice"),
    footerLabel: $("footerLabel"), footerValue: $("footerValue"), toast: $("toast"),
    progress: $("progressBlock"), sectionHeader: $("sectionHeader"), salonName: $("salonName")
  };
  var stepCopy = {
    1: ["01 / ВАШ РИТУАЛ", "Выберите услугу", "Начните с того, что хочется сделать для себя сегодня."],
    2: ["02 / ВАШ МАСТЕР", "Кому довериться?", "Выберите специалиста, с которым вам будет комфортно."],
    3: ["03 / УДОБНЫЙ МОМЕНТ", "Выберите время", "Найдём свободное окошко в вашем расписании."],
    4: ["04 / ПОЧТИ ГОТОВО", "Остался один шаг", "Проверьте детали и оставьте ваши контакты."]
  };
  var toastTimer;

  function haptic() {
    if (tg && tg.HapticFeedback) tg.HapticFeedback.selectionChanged();
  }

  function showMessage(message) {
    clearTimeout(toastTimer);
    el.toast.textContent = message;
    el.toast.hidden = false;
    toastTimer = setTimeout(function () { el.toast.hidden = true; }, 4500);
  }

  function initTelegram() {
    if (!tg) return;
    tg.ready();
    tg.expand();
    if (tg.setHeaderColor) tg.setHeaderColor("#f9f6f1");
    if (tg.setBackgroundColor) tg.setBackgroundColor("#f9f6f1");
  }

  function api(path) {
    return fetch(path).then(function (r) {
      if (!r.ok) throw new Error("Не удалось загрузить данные");
      return r.json();
    });
  }

  function formatMoney(value) {
    return Number(value || 0).toLocaleString("ru-RU") + " ₸";
  }

  function toDateParam(date) {
    return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0") + "-" + String(date.getDate()).padStart(2, "0");
  }

  function formatDayLabel(date) {
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var day = new Date(date);
    day.setHours(0, 0, 0, 0);
    var diff = Math.round((day - today) / 86400000);
    if (diff === 0) return "Сегодня";
    if (diff === 1) return "Завтра";
    return day.toLocaleDateString("ru-RU", { day: "numeric", month: "long" });
  }

  function makeNode(tag, cls, text) {
    var node = document.createElement(tag);
    if (cls) node.className = cls;
    if (text != null) node.textContent = text;
    return node;
  }

  function makeOption(config) {
    var card = makeNode("button", "option-card" + (config.selected ? " selected" : ""));
    card.type = "button";
    card.setAttribute("aria-pressed", String(!!config.selected));
    card.appendChild(makeNode("span", config.avatar ? "master-avatar" : "card-symbol", config.symbol));
    var details = makeNode("span", "details");
    details.appendChild(makeNode("span", "title", config.title));
    details.appendChild(makeNode("span", "subtitle", config.subtitle));
    card.appendChild(details);
    var end = makeNode("span", "card-end");
    if (config.price) end.appendChild(makeNode("span", "price", config.price));
    end.appendChild(makeNode("span", "card-select", "✓"));
    card.appendChild(end);
    card.addEventListener("click", config.onClick);
    return card;
  }

  function renderServices() {
    el.services.replaceChildren();
    if (!state.services.length) {
      el.services.appendChild(makeNode("p", "loading-state", "Услуги пока не добавлены. Напишите студии в Telegram."));
      return;
    }
    var symbols = ["✂", "✧", "◌", "✳", "❋", "✦"];
    state.services.forEach(function (service, index) {
      el.services.appendChild(makeOption({
        title: service.name, subtitle: service.durationMinutes + " мин · Персональный уход",
        price: formatMoney(service.priceTenge), symbol: symbols[index % symbols.length],
        selected: state.selectedService && state.selectedService.id === service.id,
        onClick: function () {
          if (!state.selectedService || state.selectedService.id !== service.id) {
            state.selectedMaster = null;
            state.selectedSlot = null;
          }
          state.selectedService = service;
          haptic(); renderServices(); updateMainButton();
        }
      }));
    });
  }

  function renderMasters() {
    el.masters.replaceChildren();
    if (!state.masters.length) {
      el.masters.appendChild(makeNode("p", "loading-state", "Для этой услуги пока нет доступных мастеров."));
      return;
    }
    state.masters.forEach(function (master) {
      el.masters.appendChild(makeOption({
        title: master.name, subtitle: "Мастер студии", symbol: master.name.trim().charAt(0).toUpperCase() || "✦", avatar: true,
        selected: state.selectedMaster && state.selectedMaster.id === master.id,
        onClick: function () {
          if (!state.selectedMaster || state.selectedMaster.id !== master.id) {
            state.selectedDate = null;
            state.selectedSlot = null;
          }
          state.selectedMaster = master;
          haptic(); renderMasters(); updateMainButton();
        }
      }));
    });
  }

  function renderDays() {
    el.days.replaceChildren();
    for (var i = 0; i < 14; i++) {
      var day = new Date();
      day.setHours(0, 0, 0, 0);
      day.setDate(day.getDate() + i);
      (function (date) {
        var selected = state.selectedDate && toDateParam(state.selectedDate) === toDateParam(date);
        var chip = makeNode("button", "day-chip" + (selected ? " selected" : ""));
        chip.type = "button";
        chip.setAttribute("aria-pressed", String(!!selected));
        chip.setAttribute("aria-label", date.toLocaleDateString("ru-RU", { dateStyle: "full" }));
        chip.appendChild(makeNode("span", "day-week", date.toLocaleDateString("ru-RU", { weekday: "short" })));
        chip.appendChild(makeNode("span", "day-number", String(date.getDate())));
        chip.appendChild(makeNode("span", "day-month", date.toLocaleDateString("ru-RU", { month: "short" })));
        chip.addEventListener("click", function () { haptic(); selectDay(date); });
        el.days.appendChild(chip);
      })(day);
    }
  }

  function selectDay(date) {
    state.selectedDate = date;
    state.selectedSlot = null;
    state.slots = [];
    renderDays(); loadSlots(); updateMainButton();
  }

  function loadSlots() {
    el.slots.replaceChildren(makeNode("p", "loading-state", "Подбираем свободное время…"));
    el.noSlots.hidden = true;
    var requestId = ++state.slotsRequestId;
    if (!state.selectedMaster || !state.selectedDate) return;
    api("/api/slots?masterId=" + encodeURIComponent(state.selectedMaster.id) + "&date=" + toDateParam(state.selectedDate))
      .then(function (slots) {
        if (requestId !== state.slotsRequestId) return;
        state.slots = slots;
        renderSlots();
      }).catch(function () {
        if (requestId !== state.slotsRequestId) return;
        el.slots.replaceChildren();
        el.noSlots.textContent = "Не получилось загрузить время. Нажмите на дату ещё раз.";
        el.noSlots.hidden = false;
      });
  }

  function renderSlots() {
    el.slots.replaceChildren();
    el.noSlots.textContent = "На этот день свободного времени нет. Выберите другую дату.";
    el.noSlots.hidden = state.slots.length !== 0;
    state.slots.forEach(function (slot) {
      var selected = state.selectedSlot && state.selectedSlot.id === slot.id;
      var chip = makeNode("button", "slot-chip" + (selected ? " selected" : ""), slot.startTime.substring(11, 16));
      chip.type = "button";
      chip.setAttribute("aria-pressed", String(!!selected));
      chip.addEventListener("click", function () {
        state.selectedSlot = slot;
        haptic(); renderSlots(); updateMainButton();
      });
      el.slots.appendChild(chip);
    });
  }

  function renderSummary() {
    el.sumService.textContent = state.selectedService.name;
    el.sumMaster.textContent = state.selectedMaster.name;
    var parts = state.selectedSlot.startTime.split("T");
    var date = parts[0].split("-").map(Number);
    el.sumTime.textContent = formatDayLabel(new Date(date[0], date[1] - 1, date[2])) + ", " + parts[1].substring(0, 5);
    el.sumPrice.textContent = formatMoney(state.selectedService.priceTenge);
    if (!el.name.value && tg && tg.initDataUnsafe && tg.initDataUnsafe.user) {
      var user = tg.initDataUnsafe.user;
      el.name.value = [user.first_name, user.last_name].filter(Boolean).join(" ");
    }
  }

  function setStep(step) {
    document.querySelectorAll(".step").forEach(function (node) { node.hidden = true; });
    $("step-" + ["", "services", "masters", "slots", "contact"][step]).hidden = false;
    state.step = step;
    el.eyebrow.textContent = stepCopy[step][0];
    el.title.textContent = stepCopy[step][1];
    el.description.textContent = stepCopy[step][2];
    el.number.textContent = "ШАГ " + String(step).padStart(2, "0") + " / 04";
    el.hint.textContent = ["", "Услуга", "Мастер", "Дата и время", "Подтверждение"][step];
    el.back.hidden = step === 1;
    el.dots.forEach(function (dot) {
      var number = Number(dot.dataset.step);
      dot.classList.toggle("done", number < step);
      dot.classList.toggle("active", number === step);
    });
    if (step === 3) {
      if (!state.selectedDate) {
        var today = new Date();
        today.setHours(0, 0, 0, 0);
        selectDay(today);
      } else { renderDays(); renderSlots(); }
    }
    if (step === 4) renderSummary();
    updateMainButton();
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function updateMainButton() {
    if (state.done) return;
    var enabled = [false, !!state.selectedService, !!state.selectedMaster, !!state.selectedSlot, true][state.step];
    el.action.disabled = !enabled || state.sending;
    el.action.innerHTML = state.step === 4 ? 'Подтвердить запись <span aria-hidden="true">→</span>' : 'Продолжить <span aria-hidden="true">→</span>';
    el.footerLabel.textContent = state.step === 3 ? "ВЫБРАННЫЙ МАСТЕР" : state.step === 4 ? "ИТОГО К ОПЛАТЕ В САЛОНЕ" : "ВАША УСЛУГА";
    if (state.step === 3 && state.selectedMaster) el.footerValue.textContent = state.selectedMaster.name;
    else if (state.step === 4 && state.selectedService) el.footerValue.textContent = formatMoney(state.selectedService.priceTenge);
    else el.footerValue.textContent = state.selectedService ? state.selectedService.name : "Выберите из списка";
  }

  function formatPhone(raw) {
    var digits = String(raw).replace(/\D/g, "");
    if (digits.charAt(0) === "7" || digits.charAt(0) === "8") digits = digits.substring(1);
    digits = digits.substring(0, 10);
    if (!digits) return { text: "", digits: "" };
    var text = "+7 (" + digits.substring(0, 3);
    if (digits.length >= 3) text += ")";
    if (digits.length > 3) text += " " + digits.substring(3, 6);
    if (digits.length > 6) text += "-" + digits.substring(6, 8);
    if (digits.length > 8) text += "-" + digits.substring(8, 10);
    return { text: text, digits: digits };
  }

  function handleAction() {
    if (state.done) { if (tg) tg.close(); return; }
    if (state.sending || el.action.disabled) return;
    haptic();
    if (state.step === 1) { setStep(2); loadMasters(); }
    else if (state.step === 2) setStep(3);
    else if (state.step === 3) setStep(4);
    else if (state.step === 4) submitBooking();
  }

  function submitBooking() {
    var name = el.name.value.trim();
    var phone = formatPhone(el.phone.value);
    if (name.length < 2) { showMessage("Пожалуйста, укажите ваше имя."); el.name.focus(); return; }
    if (phone.digits.length !== 10) { showMessage("Укажите номер телефона полностью."); el.phone.focus(); return; }
    if (!tg || !tg.initData) { showMessage("Для записи откройте приложение через Telegram-бота."); return; }
    state.sending = true;
    el.action.disabled = true;
    el.action.textContent = "Оформляем запись…";
    fetch("/api/bookings", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        initData: tg.initData, serviceId: state.selectedService.id,
        slotId: state.selectedSlot.id, clientName: name, clientPhone: "+7" + phone.digits
      })
    }).then(function (response) {
      if (response.ok) return response.json().then(function (booking) { return { ok: true, booking: booking }; });
      return response.json().catch(function () { return {}; }).then(function (error) {
        return { ok: false, status: response.status, message: error.message };
      });
    }).then(function (result) {
      if (!result.ok) { handleBookingError(result); return; }
      state.done = true;
      var booking = result.booking;
      var when = booking.startTime.replace("T", " ").substring(0, 16);
      $("doneTitle").textContent = booking.status === "PENDING" ? "Заявка отправлена" : "Вы записаны!";
      $("doneText").textContent = booking.serviceName + " · " + booking.masterName + " · " + when +
        (booking.status === "PENDING" ? ". Сообщим, когда студия подтвердит запись." : ". Ждём вас!");
      document.querySelectorAll(".step").forEach(function (node) { node.hidden = true; });
      $("step-done").hidden = false;
      el.progress.hidden = true;
      el.sectionHeader.hidden = true;
      $("bookingFooter").querySelector(".footer-detail").hidden = true;
      el.action.disabled = false;
      el.action.textContent = "Вернуться в Telegram";
      window.scrollTo({ top: 0, behavior: "smooth" });
      if (tg && tg.HapticFeedback) tg.HapticFeedback.notificationOccurred("success");
    }).catch(function () {
      showMessage("Не удалось связаться с сервером. Проверьте интернет и попробуйте снова.");
    }).finally(function () {
      state.sending = false;
      updateMainButton();
    });
  }

  function handleBookingError(result) {
    if (result.status === 409) {
      showMessage("Это время уже занято. Выберите другое свободное окошко.");
      state.selectedSlot = null;
      setStep(3);
      loadSlots();
      return;
    }
    showMessage(result.message || "Не получилось оформить запись. Попробуйте ещё раз.");
  }

  function loadMasters() {
    el.masters.replaceChildren(makeNode("div", "loading-state", "Подбираем мастеров…"));
    var id = ++state.mastersRequestId;
    api("/api/masters?serviceId=" + encodeURIComponent(state.selectedService.id)).then(function (masters) {
      if (id !== state.mastersRequestId) return;
      state.masters = masters;
      var stillAvailable = state.selectedMaster && masters.some(function (m) { return m.id === state.selectedMaster.id; });
      if (!stillAvailable) { state.selectedMaster = null; state.selectedSlot = null; }
      renderMasters(); updateMainButton();
    }).catch(function () {
      if (id !== state.mastersRequestId) return;
      el.masters.replaceChildren(makeNode("div", "loading-state", "Не получилось загрузить мастеров."));
      showMessage("Не удалось загрузить мастеров. Вернитесь к услуге и попробуйте снова.");
    });
  }

  el.phone.addEventListener("input", function () {
    var formatted = formatPhone(el.phone.value);
    el.phone.value = formatted.text;
  });
  el.back.addEventListener("click", function () {
    if (state.step > 1 && !state.sending) { haptic(); setStep(state.step - 1); }
  });
  el.action.addEventListener("click", handleAction);
  initTelegram();
  api("/api/public-config").then(function (config) {
    if (config.name) el.salonName.textContent = config.name;
  }).catch(function () { /* White-label fallback remains visible. */ });
  api("/api/services").then(function (services) {
    state.services = services;
    renderServices(); setStep(1);
  }).catch(function () {
    el.services.replaceChildren(makeNode("div", "loading-state", "Не получилось загрузить услуги. Проверьте подключение и откройте приложение снова."));
    showMessage("Сервис временно недоступен. Попробуйте позже.");
  });
})();
