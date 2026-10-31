// Ndopify — proposition page: the client's answer to a listing its agent proposed ("Ça m'intéresse" / "Pas pour
// moi"). Only enhances: without JavaScript the answers are forms that post and the #reject popup opens with :target.
// annonce.js does the popups (#reject, #visit) and the photos.
// PROTOTYPE: the answer is kept in the session; ?state=accepted|rejected|unavailable shows a state.
(function () {
  'use strict';

  var KEY = 'ndw.proposition.9001';
  var STATES = { accepted: 'ACCEPTED', rejected: 'REJECTED', unavailable: 'UNAVAILABLE' };
  var BADGES = { ACCEPTED: 'Vous intéresse', REJECTED: 'Écarté', UNAVAILABLE: 'Plus disponible' };

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

  document.addEventListener('DOMContentLoaded', function () {
    render(initialState());

    // PROTOTYPE: simulates POST /v1/matches/{id}/accept: the agent is told, the visit can be asked.
    document.querySelectorAll('[data-accept-form]').forEach(function (form) {
      form.addEventListener('submit', function (e) {
        e.preventDefault();
        // From the answer popup (below 1024px): closed first, the page then shows the visit button
        var popup = form.closest('.modal');
        if (popup) closeDialog(popup);
        answer('ACCEPTED');
        flash('Merci Sandrine ! Serge est prévenu que ce logement vous intéresse. Choisissez maintenant un ' +
          'créneau de visite.');
      });
    });

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
