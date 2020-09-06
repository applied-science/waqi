(ns applied-science.waqi
  "Data visualizations from Clojure using Vega(-Lite) in the browser.

  Waqi workflow is designed for the Clojurist who interacts with their
  data by iteratively visualizing (parts of) it, with code and
  resulting Vega-Embed simultaneously in view.
  
  Users evaluate Vega/Vega-Lite specs (as Clojure maps) with `plot!`
  to see their data visualized in the browser. If Waqi has not started
  a server and established a WebSocket connection to a browser, it
  calls `start-server!` (with or without a port argument) to do so. If
  `plot!` cannot send a WebSocket message it will throw an error.
    
  Architecture is as follows:
  
    [bare-bones HTML page on localhost]
              ^
              |
      (Vega(-Lite) specs,
           as JSON,
       sent via websocket)
              |
              v                  (Vega(-Lite) specs,
      [http-kit webserver] <---    as Clojure maps,     ---- [client]
                                sent via function call)"
  (:require [clojure.java.browse]
            [hiccup.page :as hp]
            [jsonista.core :as json]
            [org.httpkit.server :as httpkit]
            [ring.util.response :as ring]))

;; TODO consider delegating websocket interaction to Funnel:
;; https://clojureverse.org/t/announcing-the-first-release-of-funnel/6023

(defonce ^{:doc "WebSocket channel for sending Vega specs to the browser."}
  ws-chan (atom nil))

(defonce ^{:doc "Webserver: info & function to stop webserver."}
  server (atom nil))

(defonce ^{:doc "JSON interpretation of most recent EDN Vega spec passed to Waqi."}
  last-spec (atom nil))

(defn page-js
  "JavaScript snippet for the HTML page.
  Establishes a WebSocket connection to visualize incoming messages
  with Vega-Embed."
  [port]
  (str "var localSocket = new WebSocket(\"ws://localhost:" port "/ws\");
function tryParseJSON (jsonString){
    try { var o = JSON.parse(jsonString);
          if (o && typeof o === \"object\") { return o; }}
    catch (e) { }
    return false; };
localSocket.onmessage = function (event) {
    var maybeJSON = tryParseJSON(event.data);
    if (maybeJSON) { vegaEmbed('#vega-view', maybeJSON); }
    else { console.log(event.data); }};
console.log(\"Waqi connected to browser.\");"))

(defn page
  "HTML page to visualize Vega/Vega-Lite specs.
  Merely a skeleton with Vega and WebSocket connection to Vega-Embed."
  [{:keys [port] :as opts}]
  (hp/html5 [:head
             [:title "Waqi: Vega from Clojure"]
             [:meta {:charset "UTF-8"}]
             [:meta {:name "description"
                     :content "Communication to the browser"}]
             [:meta {:name "viewport"
                     :content "width=device-width, initial-scale=1"}]
             ;; TODO [:link {:href "FIXME" :rel "icon" :type "image/svg+xml"}]
             (hp/include-js "https://cdn.jsdelivr.net/npm/vega@5"
                            "https://cdn.jsdelivr.net/npm/vega-lite@4"
                            "https://cdn.jsdelivr.net/npm/vega-embed@6")
             [:script (page-js port)]]
            [:body
             [:div#vega-view]]))

(defn app
  "HTTP request handler.
  Returns HTML page set up to receive Vega specs over WebSocket,
  unless the request is a WebSocket message to be sent to that page."
  [req]
  (cond
    (:websocket? req) (httpkit/with-channel req ch
                        (reset! ws-chan ch)
                        (httpkit/send! ch "Waqi: websocket connection established."))
    (= "/" (:uri req)) {:status  200
                        :headers {"Content-Type" "text/html"}
                        :body    (str (page {:port (:server-port req)}))}
    :else (ring/resource-response (:uri req))))

(defn stop-server!
  "Tear-down fn.
  Stops Waqi webserver and `nil`ifies related vars."
  []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)
    (reset! ws-chan nil)))

(def ready-spec
  "Minimal Vega-Lite spec to show when page first loads."
  {:title {:text "Waqi"
           :font "Helvetica" :color "#333333"
           :fontSize 32 :dy 200 :dx 100
           :subtitle "Waiting for specs to plot..."
           :subtitleFontSize 18 :subtitlePadding 25}
   :width 450 :height 450})

(def loading-spec
  "Minimal Vega-Lite spec to show while loading user specs."
  (-> ready-spec
      (assoc-in [:title :text] "Loading next spec...")
      (assoc-in [:title :subtitle]
                "If you're seeing this, then either your spec is very large, or it caused an error. Check your spec (see `waqi/last-spec`) and the browser console.")
      json/write-value-as-string))

(declare plot!)

(defn start-server!
  "Setup fn.
  1. Starts Waqi webserver on given `port` or 8080
     (stopping any existing Waqi webservers)
  2. Opens HTML `page` in your browser to receive Vega specs
  3. Returns map of current setup."
  ([] (start-server! 8080))
  ([port]
   (stop-server!)
   (reset! server (httpkit/run-server #'app {:port port}))
   (clojure.java.browse/browse-url (str "http://localhost:" port))
   (while (not @ws-chan) ;; Wait a moment, then show a placeholder
     (Thread/sleep 100))
   (plot! ready-spec)
   {:port    (:local-port (meta @server))
    :server  @server
    :channel @ws-chan}))

(defn plot!* [vega-spec]
  (let [json-spec (json/write-value-as-string vega-spec)
        _ (httpkit/send! @ws-chan loading-spec)
        ws-rsp (httpkit/send! @ws-chan json-spec)
        rsp {:port    (:local-port (meta @server))
             :server  @server
             :channel @ws-chan
             :websocket-response ws-rsp}]
    (reset! last-spec json-spec)
    (if-not ws-rsp
      (throw (ex-info "Waqi's WebSocket connection to browser was lost. Either re-open the browser tab or re-start the Waqi server."
                      rsp))
      rsp)))

(defn plot!
  "Visualization fn.
  Sends the given EDN Vega spec to the browser to be visualized. Will
  start a Waqi server if none exists. Returns map of WebSocket
  response and Waqi config. Throws an error if WebSocket connection
  has been lost. Saves each spec to `last-spec` for debugging
  purposes.

  Note that if the optional `port` is provided then any Waqi server
  running on another port will be stopped."
  ([vega-spec]
   (when-not @ws-chan
     (start-server!))
   (plot!* vega-spec))
  ([port vega-spec]
   (when (or (not @ws-chan)
             (not= port (:local-port (meta @server))))
     (start-server! port))
   (plot!* vega-spec)))

;; TODO maybe provide separate, opt-in Vega spec validation?
;; TODO factor out webserver to its own lib
;; TODO maybe catch browser errors so we can give feedback here (via `throw`?)?

(comment ;;;; REPL sketches
  (start-server! 8081)

  @ws-chan

  @server

  (meta @server)

  @last-spec
  
  (plot! {:width 1000, :height 400,
          :data {:url "https://vega.github.io/vega/data/us-10m.json",
                 :format {:type "topojson", :feature "counties"}},
          :transform [{:lookup "id",
                       :from {:data {:url "https://vega.github.io/vega/data/unemployment.tsv"},
                              :key "id", :fields ["rate"]}}],
          :projection {:type "albersUsa"},
          :mark "geoshape",
          :encoding {:color {:field "rate", :type "quantitative"}}})

  (plot! 8085
         {:data {:values (map hash-map
                              (repeat :a) (range 1 20)
                              (repeat :b) (repeatedly #(* 100 (Math/random))))}
          :mark "line", :width 800, :height 600
          #_#_ :encoding {:x {:field :a, :type "ordinal", :axis {"labelAngle" 0}},
                          :y {:field :b, :type "quantitative"}}})
  
  (stop-server!)
  
  )
