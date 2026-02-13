(function() {
    const scriptUrl = document.currentScript.src;
    const baseUrl = scriptUrl.substring(scriptUrl, scriptUrl.lastIndexOf('/') + 1);

    window.multipazVerifyCredentials = async function(request) {
        try {
            const rq = await(await fetch(baseUrl + "make_request", {
                 method: 'POST',
                 headers: {
                     'Content-Type': 'application/json',
                 },
                 body: JSON.stringify(request)
            })).json();
            const sessionId = rq["session_id"];
            const dcRequest = rq["dc_request"];
            const dcResponse = await navigator.credentials.get(dcRequest);
            console.log("Response: " + JSON.stringify(dcResponse));
            return await(await fetch(baseUrl + "process_response", {
                    method: 'POST',
                    headers: {
                         'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        session_id: sessionId,
                        dc_response: dcResponse
                    })
                })).json();
        } catch (err) {
            alert("Error: " + err);
            throw err;
        }
    }
})();