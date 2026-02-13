

async function run() {
    const query = JSON.parse(document.getElementById("query").value);
    const response = await multipazVerifyCredentials({
             dcql: query
             /*,
             transaction_data: {
                type: "multipaz_test"
             }*/
         });
    const result = document.getElementById("result");
    result.innerHTML = "";
    for (let label in response.content) {
        renderContent(result, label, response.content[label], 0);
    }
}

const dataUrlPrefix = "data:image/jpeg;base64,";

function renderContent(div, label, data, depth) {
    if (typeof data == 'array') {
        depth++;
        const container = document.createElement('div');
        container.setAttribute("class", "cred_data nest" + depth);
        div.appendChild(container);
        if (label) {
            const title = document.createElement("h4");
            title.textContent = label;
            container.appendChild(title);
        }
        for (let item of data) {
            renderContent(container, "", item, depth);
        }
    } else if (typeof data == 'object') {
        depth++;
        const container = document.createElement('div');
        container.setAttribute("class", "cred_data nest" + depth);
        div.appendChild(container);
        if (label) {
            const title = document.createElement("h4");
            title.textContent = label;
            container.appendChild(title);
        }
        for (let l in data) {
            renderContent(container, l, data[l], depth);
        }
    } else if (label == 'portrait' || label == 'photo') {
        const container = document.createElement('div');
        container.setAttribute("class", "image nest" + depth);
        div.appendChild(container);
        if (label) {
            const title = document.createElement("h4");
            title.textContent = label;
            container.appendChild(title);
        }
        const image = document.createElement("img");
        image.src = dataUrlPrefix + data
        container.appendChild(image);
    } else {
        const container = document.createElement('p');
        container.setAttribute("class", "image nest" + depth);
        div.appendChild(container);
        const title = document.createElement("b");
        title.textContent = label + ": ";
        container.appendChild(title);
        container.appendChild(document.createTextNode(data + ""));
    }
}