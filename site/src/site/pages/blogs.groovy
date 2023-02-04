layout 'layouts/main.groovy', true,
        pageTitle: "The Apache Groovy programming language - Blogs",
        mainContent: contents {
            def sorted = list.sort { e -> e.value.revisionInfo.date }
            div(id: 'content', class: 'page-1') {
                div(class: 'row') {
                    div(class: 'row-fluid') {
                        div(class: 'col-lg-3') {
                            ul(class: 'nav-sidebar') {
                                li(class:'active') {
                                    a(href: '/blog/', "Blogs")
                                }
                                sorted.reverseEach { blog ->
                                    li { a(href: blog.key, blog.key) }
                                }
                            }
                        }

                        div(class: 'col-lg-8 col-lg-pull-0') {
                            h1('Blogs for Groovy')
                            p 'Here you can find the Blogs for the Groovy programming language:'
                            ul {
                                sorted.reverseEach { k, v ->
                                    li {
                                        div(class: 'name') {
                                            a(href: k, v.documentTitle.main)
                                            p("Posted by $v.author on $v.revisionInfo.date")
                                            p(v.attributes.description)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
