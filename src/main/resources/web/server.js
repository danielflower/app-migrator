document.addEventListener('DOMContentLoaded', () => {
    const $ = document.querySelector.bind(document);


    $('.urlForm').addEventListener('submit', e => {
        e.preventDefault();
        let url = $('.urlForm input[name="appRunnerUrl"]').value;
        url = url.replace(/\/$/, '');

        const errDiv = $('.urlForm .error');

        const runnersUrl = url + '/api/v1/runners';
        fetch(runnersUrl)
            .then( r => {
                return r.ok ? r.json() : r.status + ' returned from ' + runnersUrl;
            })
            .then( r => console.log(r))
            .catch(e => {
                errDiv.textContent = 'Error: ' + e;
            });



        console.log(url);

    });
});