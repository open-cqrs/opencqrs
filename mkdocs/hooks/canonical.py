"""Canonical URL of blog posts.

The blog posts' primary home is the Digital Frontiers website, at the same
path as here (blog/<slug>/). Every post announces that copy as its
<link rel="canonical"> and og:url, so search engines index it rather than
this one. A post's front matter `canonical_url` overrides the derived URL.
"""

from urllib.parse import urljoin

WEBSITE = "https://www.digitalfrontiers.de/"


# on_page_markdown, not later: the social plugin writes og:url from the
# canonical URL in on_page_content.
def on_page_markdown(markdown, page, config, files):
    if page.file.src_uri.startswith("blog/posts/"):
        page.canonical_url = page.meta.get("canonical_url") or urljoin(WEBSITE, page.url)
    return markdown
