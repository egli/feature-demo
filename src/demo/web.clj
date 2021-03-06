(ns demo.web
  (:require [immutant.web             :as web]
            [immutant.web.async       :as async]
            [immutant.web.sse         :as sse]
            [immutant.web.middleware  :as immutant]
            [compojure.route          :as route]
            [compojure.core     :refer (ANY GET defroutes)]
            [ring.util.response :refer (response redirect content-type)]
            [clojure.pprint     :refer (pprint)]
            [environ.core       :refer (env)]))

(defn echo
  "Echos the request back as a string."
  [request]
  (-> (response (with-out-str (pprint request)))
    (content-type "text/plain")))

(defn counter
  "From https://github.com/ring-clojure/ring/wiki/Sessions"
  [{session :session}]
  (let [count (:count session 0)
        session (assoc session :count (inc count))]
    (println "counter =>" count)
    (-> (response (str "You accessed this page " count " times\n"))
      (assoc :session session))))

(defn sse-countdown
  [request]
  (sse/as-channel request
    {:on-open (fn [ch]
                (doseq [x (range 5 0 -1)]
                  (sse/send! ch x)
                  (Thread/sleep 500))
                ;; Signal the client to call EventSource.close()
                (sse/send! ch {:event "close", :data "bye!"}))}))

(defroutes routes
  (GET "/" {c :context} (redirect (str c "/index.html")))
  (GET "/counter" [] counter)
  (GET "/sse" [] sse-countdown)
  (route/resources "/")
  (ANY "*" [] echo))

(def websocket-callbacks
  "WebSocket callback functions"
  {:on-open    (fn [channel]
                 (async/send! channel "Ready to reverse your messages!"))
   :on-close   (fn [channel {:keys [code reason]}]
                 (println "close code:" code "reason:" reason))
   :on-message (fn [ch m]
                 (async/send! ch (apply str (reverse m))))})

(defn -main [& {:as args}]
  (web/run
    (-> routes
      (immutant/wrap-session {:timeout 20})
      (immutant/wrap-websocket websocket-callbacks))
    (merge {"host" (env :demo-web-host), "port" (env :demo-web-port)}
      args)))
