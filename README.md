# Waqi - النسر‌الواقع

Waqi is a small Clojure library for REPL-driven data visualization with [Vega][3]([-Lite][4]) displayed in a browser.

The name comes from the [etymology for Vega][1], the star:

>Vega, from the Arabic al-Waqi', contraction of an-Nasr al-Waqi' (النسر‌الواقع) "swooping eagle", from an-Nasr "eagle, vulture" + al-Waqi' "falling, swooping".


## Installation
We have not yet released to clojars, so the recommended installation is with deps.edn:

``` clojure
applied-science/waqi {:git/url "https://github.com/applied-science/waqi/"
                      :sha "FIXME"}
```


## Usage
Add `[applied-science.waqi :as waqi]` to your `ns` declaration‘s `:require`s.

Then `waqi/plot!` a Clojure map describing a Vega or Vega-lite spec.
``` clojure
(waqi/plot! {:data {:values (map hash-map
                                 (repeat :a) (range 1 20)
                                 (repeat :b) (repeatedly #(Math/random)))}
             :mark "bar",
             :encoding {:x {:field :a, :type "ordinal"},
                        :y {:field :b, :type "quantitative"}}})
```

The resulting visualization should appear in your browser as a [Vega-Embed][5]. A new browser tab will open when the Waqi server is started, which is either the first call to `plot!` or every call to `start-server!`. Both functions optionally accept a port argument. 

Only one Waqi port is open at a time. When you‘re done, you should `(waqi/stop-server!)` to free up the port. If you want to run on a non-default port, you have two options:

``` clojure
;;;; Option one: set the port in `plot!`.
(waqi/plot! 8081
            {:width 800, :height 400,
             :data {:url "https://vega.github.io/vega/data/us-10m.json",
                    :format {:type "topojson", :feature "counties"}},
             :transform [{:lookup "id",
                          :from {:data {:url "https://vega.github.io/vega/data/unemployment.tsv"},
                                 :key "id", :fields ["rate"]}}],
             :projection {:type "albersUsa"},
             :mark "geoshape",
             :encoding {:color {:field "rate", :type "quantitative"}}})


;;;; Option two: set the port by explicitly starting the server.
(waqi/start-server! 8081)

;; Then we can leave the port out of calls to `plot!`:
(waqi/plot! {:data {:values (map hash-map
                                 (repeat :a) (range 1 20)
                                 (repeat :b) (repeatedly #(* 100 (Math/random))))}
             :mark "line",
             :width 800, :height 600
             :encoding {:x {:field :a, :type "ordinal", :axis {"labelAngle" 0}},
                        :y {:field :b, :type "quantitative"}}})

;; Either way, when we're done we clean up:
(waqi/stop-server!)
```

Let's take a moment to look at the return value from `plot!`:

``` clojure
{:port 8081, 
 :server #function[clojure.lang.AFunction/1],
 :channel #object[org.httpkit.server.AsyncChannel 0x1c576f2e "/0:0:0:0:0:0:0:1:8081<->/0:0:0:0:0:0:0:1:63946"],
 :websocket-response true}
```
This function's main purpose is the side effect of seeing a spec plotted in the browser, so the return value is primarily informational. It is occasionally useful when juggling multiple project REPLs to be reminded which port a particular visualization is on. Implementation details like `server` and `channel` are exposed in case you want to do something unusual. The webserver's `websocket-response` should always be `true` because if it was otherwise then the websocket was closed and we should be getting an error:

``` clojure
Unhandled clojure.lang.ExceptionInfo
   Waqi's WebSocket connection to browser was lost. Either re-open the
   browser tab or re-start the Waqi server.
   {:port 8081,
    :server #function[clojure.lang.AFunction/1],
    :channel
    #object[org.httpkit.server.AsyncChannel 0x1c576f2e "0.0.0.0/0.0.0.0:8081<->null"],
    :websocket-response false}
```
Waqi fails noisily because one of its design goals is to prevent the user from seeing a previously visualized spec under the impression it is the current spec. This is why Waqi shows a "loading" message: the user doesn't see an old version of their spec while large specs (such as a very detailed geoJSON) are being rendered. This also helps with some kinds of invalid specs (e.g. `(waqi/plot! {:mark "line"})`), which have no trouble getting to the browser, but cannot be visualized.


## Other Vega libraries
There are many, many ways to work with Vega/Vega-Lite from Clojure. The SciCloj hub maintains a [listing][6] of most of them.

Waqi is most similar to [Oz][2]. They share a browser-based workflow, but Oz provides much more functionality: integration with Jupyter notebooks and GitHub gists, creation of dynamic and static websites centered around a visualization, multiple live-coding workflows, and much more. Waqi focuses on just one of those features: sending Vega/Vega-Lite specs from the REPL to a browser window. This allows Waqi to minimize dependencies and lines of code. The author of Oz has said, "Oz's objective is to be the Clojurist's Swiss Army knife for working with Vega-Lite & Vega." It might be Waqi's goal to be just the nail file.

My own workflow is to start with [emacs-vega-view][7] for quick one-off visualizations. If the code needs to be shared with non-emacs users, or if the chart just feels better in a separate window, then I'll reach for either Waqi or [Hubble][9] to continue working from the REPL. If I need programmatic output instead of interactive visualization I reach for [darkstar][10] or the vega CLI.


## License

Copyright © 2020 Applied Science

Distributed under the MIT License.


[1]: http://dictionary.obspm.fr/index.php?showAll=1&formSearchTextfield=Vega
[2]: https://github.com/metasoarous/oz
[3]: https://vega.github.io/vega/
[4]: https://vega.github.io/vega-lite/
[5]: https://github.com/vega/vega-embed
[6]: https://scicloj.github.io/pages/libraries/#vega_rendering
[7]: https://github.com/applied-science/emacs-vega-view
[8]: https://github.com/jsa-aerial/hanami
[9]: https://github.com/applied-science/hubble
[10]: https://github.com/applied-science/darkstar
