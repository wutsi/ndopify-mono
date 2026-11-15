/*
 * Prototype only: shows the /telegram page's server states from the URL, since the static page has no POST.
 *   ?sent=1               the "code sent" confirmation
 *   ?error=already        the 409 message
 *   ?error=failed         the send failure
 * The served page renders the same blocks from the flash attributes, without JavaScript.
 */
(function () {
    "use strict";

    var params = new URLSearchParams(window.location.search);
    var sent = document.querySelector("[data-telegram-sent]");
    var error = document.querySelector("[data-telegram-error]");
    var errorText = document.querySelector("[data-telegram-error-text]");

    if (sent && params.get("sent") === "1") {
        sent.hidden = false;
    }

    var messages = {
        already: "Votre compte Telegram est déjà connecté.",
        failed: "L'envoi du code a échoué. Réessayez dans quelques instants."
    };
    var message = messages[params.get("error")];
    if (error && errorText && message) {
        errorText.textContent = message;
        error.hidden = false;
    }
})();
