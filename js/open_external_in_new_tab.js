document.addEventListener("DOMContentLoaded", function () {
    const links = document.querySelectorAll("a[href^='http']");
    links.forEach(function (link) {
        if (!link.href.includes(window.location.hostname)) {
            link.setAttribute("target", "_blank");
            link.setAttribute("rel", "noopener");
        }
    });
});
