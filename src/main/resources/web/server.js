document.addEventListener('DOMContentLoaded', () => {
    const $ = document.querySelector.bind(document);

    const qs = new URLSearchParams(location.search);
    const routerUrl = (qs.get('routerUrl') || '').replace(/\/$/, '');
    const errDiv = $('.error');
    const instanceListTemplate = $('#instanceListTemplate');
    const instancesList = $('#instancesList');

    if (routerUrl) {
        const runnersUrl = routerUrl + '/api/v1/runners';
        fetch(runnersUrl)
            .then(r => {
                return r.ok ? r.json() : r.status + ' returned from ' + runnersUrl;
            })
            .then(r => {
                for (let runner of r.runners) {
                    console.log(runner);
                    let li = document.importNode(instanceListTemplate.content, true);
                    let li$ = li.querySelector.bind(li);
                    li$('a.instanceName').textContent = runner.id;
                    li$('a.instanceName').href = 'migrate-server.html?routerUrl=' + encodeURIComponent(routerUrl) + '&instanceId=' + encodeURIComponent(runner.id);
                    li$('.curCount').textContent = runner.appCount;
                    li$('.maxCount').textContent = runner.maxApps;
                    li$('a.appLinks').href = runner.appsUrl;
                    instancesList.appendChild(li);
                }
            })
            .catch(e => {
                errDiv.textContent = 'Error: ' + e;
            });
    }


});