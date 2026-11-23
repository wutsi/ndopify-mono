// Ndopify — proposition page: the client's answer to a listing its agent proposed ("Ça m'intéresse" / "Pas pour
// moi"). Only enhances: without JavaScript the answers are forms that post and the #reject popup opens with :target.
// annonce.js does the popups (#reject, #visit) and the photos; this file does the #visit form (no contact fields: the
// mandate's).
// PROTOTYPE: the answer is kept in the session (it is final: no way back from REJECTED or ACCEPTED);
// ?state=accepted|accepted-no-visit|visit-requested|rejected|expired|unavailable shows a
// state, &type=video a video visit requested, &sale=1 the buyer's SALE search (its own kept answer, visit links that
// stay on the sale variant).
// EXPIRED: the client didn't answer within 7 days, no answer anymore (set by the server only, never from here).
(function () {
  'use strict';

  var KEY = 'ndw.proposition.9001';
  // v2 (#259): ?sale=1 is the buyer's SALE search — its own kept answer, and visit links that stay on the sale variant.
  // v2 (#274): ?building=1 is a building sold as a whole (a sale too).
  var BUILDING = new URLSearchParams(location.search).get('building') === '1';
  var SALE = new URLSearchParams(location.search).get('sale') === '1' || BUILDING;
  if (SALE) KEY += '.sale';
  if (BUILDING) KEY += '.building';
  var STATES = {
    accepted: 'ACCEPTED', 'accepted-no-visit': 'ACCEPTED_NO_VISIT', 'visit-requested': 'VISIT_REQUESTED', rejected: 'REJECTED', expired: 'EXPIRED',
    unavailable: 'UNAVAILABLE'
  };
  var BADGES = {
    ACCEPTED: 'Vous intéresse', ACCEPTED_NO_VISIT: 'Vous intéresse', VISIT_REQUESTED: 'Visite demandée', REJECTED: 'Écarté', UNAVAILABLE: 'Plus disponible'
  };

  function visitUrl(state, video) {
    return 'visite.html?state=' + state + (video ? '&type=video' : '') + (SALE ? '&sale=1' : '') +
      (BUILDING ? '&building=1' : '');
  }

  var store = {
    get: function () {
      try { return window.sessionStorage.getItem(KEY); } catch (e) { return null; }
    },
    set: function (value) {
      try { window.sessionStorage.setItem(KEY, value); } catch (e) { /* private mode: the page still shows it */ }
    }
  };

  function initialState() {
    var param = STATES[new URLSearchParams(location.search).get('state')];
    return param || store.get() || 'PENDING';
  }

  function render(state) {
    document.querySelectorAll('[data-answer], [data-bar-answer]').forEach(function (block) {
      block.hidden = (block.getAttribute('data-answer') || block.getAttribute('data-bar-answer')) !== state;
    });
    var badge = document.querySelector('[data-state-badge]');
    badge.hidden = !BADGES[state];
    badge.textContent = BADGES[state] || '';

    // The next step, at the top: request a visit once interested, or open it afterwards
    var next = document.querySelector('[data-next-step]');
    if (next) {
      var action = next.querySelector('[data-next-step-action]');
      var label = next.querySelector('[data-next-step-label]');
      var visitLink = document.querySelector('[data-answer="VISIT_REQUESTED"] a');
      next.hidden = state !== 'ACCEPTED' && state !== 'VISIT_REQUESTED';
      if (state === 'ACCEPTED') {
        action.setAttribute('data-modal-open', '');
        action.setAttribute('href', '#visit');
        label.textContent = 'Demander une visite';
      } else if (state === 'VISIT_REQUESTED') {
        action.removeAttribute('data-modal-open');
        action.setAttribute('href', visitLink ? visitLink.getAttribute('href') : visitUrl('requested'));
        label.textContent = 'Voir ma visite';
      }
    }
  }

  function flash(text) {
    var box = document.querySelector('[data-proposal-flash]');
    box.querySelector('[data-proposal-flash-text]').textContent = text;
    box.hidden = false;
    box.scrollIntoView({ behavior: 'smooth', block: 'center' });
    box.focus({ preventScroll: true });
  }

  function answer(state) {
    store.set(state);
    render(state);
  }

  function closeDialog(dialog) {
    var close = dialog.querySelector('[data-modal-close]');
    if (close) close.click();
  }

  // The day card's parts (sam. / 7 / nov.) as the tenant's long date, with the year: "samedi 7 novembre 2026"
  var DAYS = ['dimanche', 'lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi'];
  var MONTHS = ['janvier', 'février', 'mars', 'avril', 'mai', 'juin', 'juillet', 'août', 'septembre', 'octobre',
    'novembre', 'décembre'];

  function longDate(iso, time) {
    var parts = iso.split('-').map(Number);
    var date = new Date(parts[0], parts[1] - 1, parts[2]);
    return DAYS[date.getDay()] + ' ' + date.getDate() + ' ' + MONTHS[date.getMonth()] + ' ' + date.getFullYear() +
      ' à ' + time;
  }

  function showError(control, message) {
    var error = control.closest('.field').querySelector('.field-error');
    control.setAttribute('aria-invalid', String(!!message));
    if (error) error.textContent = message;
    return !message;
  }

  var TYPES = { ON_SITE: 'Sur place', VIDEO: 'Visite vidéo' };

  function showSlot(slot) {
    document.querySelectorAll('[data-visit-slot]').forEach(function (n) {
      n.textContent = slot;
    });
  }

  // PROTOTYPE: simulates POST /v1/visits { matchId, type, requestedAt, note }: the visit is REQUESTED, the agent e-mailed.
  // "Envoyer la demande" stays inactive until a day and a time are chosen.
  function initVisitRequest(modal) {
    var form = modal && modal.querySelector('[data-visit-request-form]');
    if (!form) return;
    var done = modal.querySelector('[data-interest-done]');
    var submit = form.querySelector('[type="submit"]');
    var day = form.querySelector('[name="visitDate"]');
    var time = form.querySelector('#visit-time');

    function refresh() {
      var filled = !!form.querySelector('[name="visitDate"]:checked') && time.value !== '';
      submit.setAttribute('aria-disabled', String(!filled));
    }

    form.addEventListener('change', refresh);
    refresh();

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var checked = form.querySelector('[name="visitDate"]:checked');
      var ok = [
        showError(day, checked ? '' : 'Choisissez le jour de la visite.'),
        showError(time, time.value ? '' : 'Choisissez l\'heure de la visite.')
      ].every(Boolean);
      if (!ok) {
        form.querySelector('[aria-invalid="true"]').focus();
        return;
      }
      var type = form.querySelector('[name="type"]:checked').value;
      var slot = longDate(checked.value, time.options[time.selectedIndex].text);
      done.querySelector('[data-interest-done-text]').textContent = 'Vous avez demandé une ' +
        (type === 'VIDEO' ? 'visite vidéo' : 'visite sur place') + ' le ' + slot +
        '. Serge vous confirme la date par e-mail.';
      showSlot(TYPES[type] + ' · ' + slot);
      done.querySelector('.btn-primary').href = visitUrl('requested', type === 'VIDEO');
      document.querySelectorAll('[data-answer="VISIT_REQUESTED"] a, [data-bar-answer="VISIT_REQUESTED"] a')
        .forEach(function (a) {
          a.href = done.querySelector('.btn-primary').href;
        });
      form.hidden = true;
      done.hidden = false;
      done.focus();
      answer('VISIT_REQUESTED');
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    render(initialState());
    if (SALE) {
      document.title = BUILDING
        ? 'Un bien pour vous : immeuble 12 unités, Bonapriso — Douala | Ndopify'
        : 'Un bien pour vous : villa 4 chambres, Bonapriso — Douala | Ndopify';
      // The page's own visit links (the VISIT_REQUESTED answer) stay on the sale variant.
      document.querySelectorAll('a[href^="visite.html?"]').forEach(function (a) {
        a.href = a.getAttribute('href') + '&sale=1' + (BUILDING ? '&building=1' : '');
      });
    }
    if (new URLSearchParams(location.search).get('type') === 'video') {
      showSlot(TYPES.VIDEO + ' · samedi 7 novembre 2026 à 10h30');
      document.querySelectorAll('a[href^="visite.html?state=requested"]').forEach(function (a) {
        a.href = visitUrl('requested', true);
      });
    }

    // PROTOTYPE: simulates POST /v1/matches/{id}/accept: the agent is told, the visit can be asked.
    document.querySelectorAll('[data-accept-form]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        // From the answer popup (below 1024px): closed first, the page then shows the visit button
        var popup = form.closest('.modal');
        if (popup) closeDialog(popup);
        answer('ACCEPTED');
        flash('Merci Sandrine ! Serge est prévenu que ' + (SALE ? 'ce bien' : 'ce logement') + ' vous intéresse. ' +
          'Choisissez maintenant un créneau de visite.');
      });
    });

    initVisitRequest(document.getElementById('visit'));

    // PROTOTYPE: simulates POST /v1/matches/{id}/reject { reason, comment }.
    var dialog = document.getElementById('reject');
    var form = dialog.querySelector('[data-reject-form]');
    var error = form.querySelector('.reject-reasons .field-error');
    form.addEventListener('change', function () {
      error.textContent = '';
    });
    form.addEventListener('submit', function (e) {
      e.preventDefault();
      if (!form.querySelector('[name="reason"]:checked')) {
        error.textContent = 'Choisissez la raison principale.';
        form.querySelector('[name="reason"]').focus();
        return;
      }
      closeDialog(dialog);
      form.reset();
      answer('REJECTED');
      flash('C\'est noté : Serge est prévenu et continue de chercher pour vous.');
    });
  });
})();
