document.addEventListener('DOMContentLoaded', () => {
    const $ = document.querySelector.bind(document);

    const qs = new URLSearchParams(location.search);
    const goButton = $('#goButton');
    const routerUrl = (qs.get('routerUrl') || '').replace(/\/$/, '');
    const runnerId = qs.get('instanceId');
    const errDiv = $('.error');
    let isRunning = false;

    if (routerUrl && runnerId) {
        const runnerUrl = routerUrl + '/api/v1/runners/' + encodeURIComponent(runnerId);
        fetch(runnerUrl)
            .then(r => r.ok ? r.json() : r.status + ' returned from ' + runnerUrl)
            .then(r => fetch(r.appsUrl).then(r => r.ok ? r.json() : r.status + ' returned from ' + r.appsUrl))
            .then(r => {
                console.log(r);
                const template = $('#appTemplate');
                const appsContainer = $('#appsFieldset');
                for (let app of r.apps) {
                    const div = document.importNode(template.content, true);
                    const d$ = div.querySelector.bind(div);
                    d$('.appName').textContent = app.name;
                    d$('input').name = app.name;
                    appsContainer.appendChild(div);
                }
                goButton.removeAttribute('disabled');
            })
            .catch(e => {
                errDiv.textContent = 'Error: ' + e;
            });
    } else {
        alert('routerUrl and/or instanceId query string parameters missing');
    }

    const resolvedPromise = () => new Promise(resolve => resolve());

    const decoder = new TextDecoder();

    function pump(reader, output) {
        return reader.read().then(function (result) {
            if (result.done) {
                return;
            }
            const chunk = decoder.decode(result.value || new Uint8Array, {
                stream: !result.done
            });
            output.appendChild(document.createTextNode(chunk));

            output.scrollTop = output.scrollHeight;

            return pump(reader, output);
        });
    }

    function migrate(name, appDiv, a$) {
        appDiv.scrollIntoView({behavior: 'smooth'});
        const params = new URLSearchParams();
        params.append('routerUrl', routerUrl);
        params.append('appName', name);
        params.append('currentRunnerID', runnerId);
        const overflower = a$('.overflower');
        overflower.classList.remove('success', 'failer');
        overflower.classList.add('inProgress');
        return fetch('/app-migrator/api/emigrate', {
            method: 'post',
            headers: {
                'Accept': 'text/plain',
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: params
        }).then(res => pump(res.body.getReader(), a$('.output')))
            .then(c => {
                console.log('Completed migration of ' + name);
                overflower.classList.remove('inProgress');
                overflower.classList.add('success');
            }).catch(e => {
                overflower.classList.remove('inProgress');
                overflower.classList.add('failure');
                console.log('Error migrating ' + name, e);
                errDiv.textContent = 'Error migrating ' + name + ' - ' + e;
            });
    }

    function stop() {
        console.log('Going to stop');

    }

    function start() {
        console.log('Going to start');
        isRunning = true;
        let curTask = resolvedPromise();
        if ($('input[name="disableNew"]').checked) {
            // TODO: set maxApps=0 for current runner
            // curTask = fetch()
        }

        const appDivs = document.querySelectorAll('div.app');
        for (let appDiv of appDivs) {
            let a$ = appDiv.querySelector.bind(appDiv);
            curTask = curTask.then(function () {
                let cb = a$('input');
                console.log(cb.name + ' migrating? ' + cb.checked);
                return cb.checked
                    ? migrate(cb.name, appDiv, a$)
                    : resolvedPromise();

            });
        }

        if ($('input[name="removeRunner"]').checked) {
            // TODO: delete instance from router
            // curTask = fetch()
        }


        curTask.then(e => {
            console.log('finished, ', e);
        });
    }

    goButton.addEventListener('click', e => {
        if (isRunning) {
            stop();
        } else {
            start();
        }
    });


});