// Uploads one file straight to the storage, for a form whose action also answers "<action>/presign" and
// "<action>/attach" (JSON). Resolves true once attached, false when the direct path can't run (local storage,
// no bucket CORS, expired URL, any error): the caller then posts the file to the form as usual.
(function () {
    function upload(action, file) {
        return fetch(action + "/presign", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ contentType: file.type })
        }).then(function (response) {
            return response.ok ? response.json() : null;
        }).then(function (presigned) {
            if (!presigned || !presigned.url) return false;
            return fetch(presigned.url, {
                method: "PUT",
                headers: { "Content-Type": file.type },
                body: file
            }).then(function (put) {
                if (!put.ok) return false;
                return fetch(action + "/attach", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ path: presigned.path })
                }).then(function (attached) {
                    return attached.ok;
                });
            });
        }).catch(function () {
            return false;
        });
    }

    window.NdDirectUpload = { upload: upload };
})();
