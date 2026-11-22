// Ndopify — visit page (templates/visit.html): listing.js opens and closes the #cancel popup; this moves the focus into
// it, to the reason textarea, when it opens. Without JavaScript the popup opens with :target and its form posts.
(function () {
  'use strict';

  function focusReason() {
    var dialog = document.getElementById('cancel');
    var reason = dialog && dialog.querySelector('textarea');
    if (reason) reason.focus();
  }

  document.addEventListener('click', function (e) {
    if (!e.target.closest('[data-modal-open][href="#cancel"]')) return;
    // After listing.js has opened it (its handler runs first: it was added first)
    setTimeout(focusReason, 0);
  });

  document.addEventListener('DOMContentLoaded', function () {
    if (location.hash === '#cancel') focusReason();
  });
})();
