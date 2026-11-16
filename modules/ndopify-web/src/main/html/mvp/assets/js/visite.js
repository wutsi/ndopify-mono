// Ndopify — visit page: the customer's visit, one block per state. Only enhances: without JavaScript the forms post
// and the #cancel popup opens with :target. annonce.js opens and closes the popup.
// PROTOTYPE: ?state=… shows a state (default confirmed), &type=video its video variant and the forms fake the server's answers, moving to the
// next state (the address follows it, so a reload keeps it).
(function () {
  'use strict';

  var STATES = {
    requested: 'REQUESTED',
    confirmed: 'CONFIRMED',
    'checked-in': 'CHECKED_IN',
    completed: 'COMPLETED',
    'feedback-sent': 'FEEDBACK_SENT',
    'feedback-sent-no': 'FEEDBACK_SENT_NO',
    cancelled: 'CANCELLED',
    'cancelled-by-agent': 'CANCELLED_BY_AGENT',
    expired: 'EXPIRED',
    'no-show': 'NO_SHOW',
    'no-show-agent': 'NO_SHOW_AGENT'
  };

  // The badge (text, tone) and the current step (1–3, 4 = all done, 0 = no progress: an ended visit)
  var VIEW = {
    REQUESTED: ['Demandée', 'info', 2],
    CONFIRMED: ['Confirmée', 'success', 3],
    CHECKED_IN: ['En cours', 'info', 3],
    COMPLETED: ['Terminée', 'success', 4],
    FEEDBACK_SENT: ['Terminée', 'success', 4],
    FEEDBACK_SENT_NO: ['Terminée', 'success', 4],
    CANCELLED: ['Annulée', 'neutral', 0],
    CANCELLED_BY_AGENT: ['Annulée', 'neutral', 0],
    EXPIRED: ['Expirée', 'neutral', 0],
    NO_SHOW: ['Non effectuée', 'neutral', 0],
    NO_SHOW_AGENT: ['Non effectuée', 'neutral', 0]
  };

  var params = new URLSearchParams(location.search);
  // ON_SITE or VIDEO: the [data-visit-type] variants
  var type = params.get('type') === 'video' ? 'VIDEO' : 'ON_SITE';

  function has(node, attr, state) {
    return node.getAttribute(attr).split(/\s+/).indexOf(state) !== -1;
  }

  function render(state) {
    document.querySelectorAll('[data-visit-state]').forEach(function (block) {
      block.hidden = !has(block, 'data-visit-state', state);
    });
    // A variant shows when both its state and its type (if it has them) are the current ones
    document.querySelectorAll('[data-visit-only], [data-visit-type]').forEach(function (node) {
      node.hidden = (node.hasAttribute('data-visit-only') && !has(node, 'data-visit-only', state)) ||
        (node.hasAttribute('data-visit-type') && !has(node, 'data-visit-type', type));
    });
    var view = VIEW[state];
    var badge = document.querySelector('[data-visit-badge]');
    badge.textContent = view[0];
    badge.className = 'badge visit-badge visit-badge--' + view[1];
    var steps = document.querySelector('[data-visit-steps]');
    steps.hidden = view[2] === 0;
    Array.prototype.forEach.call(steps.children, function (li, i) {
      li.classList.toggle('is-done', i + 1 < view[2]);
      li.classList.toggle('is-current', i + 1 === view[2]);
      if (i + 1 === view[2]) li.setAttribute('aria-current', 'step');
      else li.removeAttribute('aria-current');
    });
  }

  function go(state) {
    render(state);
    var param = Object.keys(STATES).filter(function (k) { return STATES[k] === state; })[0];
    history.replaceState(null, '', location.pathname + '?state=' + param + (type === 'VIDEO' ? '&type=video' : ''));
    var block = Array.prototype.filter.call(document.querySelectorAll('[data-visit-state]'), function (b) {
      return !b.hidden;
    })[0];
    var heading = block && Array.prototype.filter.call(block.querySelectorAll('h2'), function (h) {
      return !h.hidden;
    })[0];
    window.scrollTo({ top: 0, behavior: 'smooth' });
    if (heading) {
      heading.setAttribute('tabindex', '-1');
      heading.focus({ preventScroll: true });
    }
  }

  function showError(control, message) {
    var error = control.closest('.field').querySelector('.field-error');
    control.setAttribute('aria-invalid', String(!!message));
    if (error) error.textContent = message;
    return !message;
  }

  document.addEventListener('DOMContentLoaded', function () {
    render(STATES[params.get('state')] || 'CONFIRMED');

    // PROTOTYPE: simulates POST /v1/visits/{id}/feedback { interest, reasonCode, comment }: once only.
    var feedback = document.querySelector('[data-feedback-form]');
    feedback.addEventListener('change', function (e) {
      showError(e.target, '');
    });
    feedback.addEventListener('submit', function (e) {
      e.preventDefault();
      var interest = feedback.querySelector('[name="interest"]:checked');
      var reason = feedback.querySelector('[name="reasonCode"]:checked');
      var notInterested = interest && interest.value === 'NOT_INTERESTED';
      var ok = [
        showError(feedback.querySelector('[name="interest"]'), interest ? '' : 'Répondez par oui ou par non.'),
        showError(feedback.querySelector('[name="reasonCode"]'),
          notInterested && !reason ? 'Choisissez la raison principale.' : '')
      ].every(Boolean);
      if (!ok) {
        feedback.querySelector('[aria-invalid="true"]').focus();
        return;
      }
      go(notInterested ? 'FEEDBACK_SENT_NO' : 'FEEDBACK_SENT');
    });

    // PROTOTYPE: simulates POST /v1/visits/{id}/cancel { reason }: CANCELLED by the customer, the agent e-mailed.
    var dialog = document.getElementById('cancel');
    var cancel = dialog.querySelector('[data-cancel-form]');
    cancel.addEventListener('submit', function (e) {
      e.preventDefault();
      var close = dialog.querySelector('[data-modal-close]');
      if (close) close.click();
      cancel.reset();
      go('CANCELLED');
    });

    // annonce.js opens the popup; the focus goes to its reason
    document.addEventListener('click', function (e) {
      if (!e.target.closest('[data-modal-open][href="#cancel"]')) return;
      setTimeout(function () { dialog.querySelector('textarea').focus(); }, 0);
    });
  });
})();
