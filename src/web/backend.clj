(ns web.backend
  (:require
    [ring.adapter.jetty :refer [run-jetty]]
    [clojure.string :as str]
    [hiccup.page :as page]
    [ring.middleware.resource :refer [wrap-resource]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.middleware.session :refer [wrap-session]]
    [chatbot.identify_keyword :refer [keyword-response-main]]
    [chatbot.parse :refer [parse-input]]
    [chatbot.bot_utils :as bot]
    [chatbot.park_utils :as park]
    [ring.util.response :refer [response]]))

; TODO: keep separate chat history for different parks
(def chat-map (ref (hash-map)))

(defn chatbot-page [content current-uri]
  (page/html5
    [:head
     [:title "Chatbot"]
    (page/include-css "style.css")
    (page/include-js "script.js")]
    [:body
     [:div {:id "content"}
      [:h1 "Prague parks chatbot"]
      [:p [:a {:href "/"} "Home"]]
      [:h3 (str "Current park: " (park/keyword->park
                                   (str/replace current-uri #"/" "")))]
     [:div {:id "chat-window"}
      content]
     [:form {:method "POST" :action current-uri}
      [:input
       {:type "text" :autofocus "autofocus" :id "input" :name "input"}]]]]))

(defn index []
  (page/html5
    [:head
     [:title "Chatbot"]
    (page/include-css "style.css")]
    [:body
      [:h1 "Prague parks chatbot"]
      [:h2 "List of parks: "]
      [:p
       [:a {:href "/riegerovy-sady"}
       "Riegerovy Sady"]]
      [:p
       [:a {:href "/klamovka"}
       "Klamovka"]]
      [:p
       [:a {:href "/letna"}
       "Letna"]]
      [:p
       [:a {:href "/stromovka"}
       "Stromovka"]]
      [:p
       [:a {:href "/ladronka"}
       "Ladronka"]]
      [:p
       [:a {:href "/frantiskanska-zahrada"}
       "Frantiskanska zahrada"]]
      [:p
       [:a {:href "/kampa"}
       "Kampa"]]
      [:p
       [:a {:href "/petrin"}
       "Petrin"]]
      [:p
       [:a {:href "/kinskeho-zahrada"}
       "Kinskeho zahrada"]]
      [:p
       [:a {:href "/vysehrad"}
      "Vysehrad"]]]))

(defn chatbot-respond! [session user-input]
  (let [parsed-input (keyword-response-main (parse-input user-input))
        ret-str (if parsed-input (park/find-park-data parsed-input)
                                 (rand-nth bot/possible-error-messages))]
    (dosync
      (ref-set chat-map
               (assoc @chat-map
                      (keyword (str (:id session)))
                      (str (get @chat-map
                                (keyword (str (:id session))))
                           "<p class=\"bot-msg\">" ret-str "</p>"))))))

(def list-of-park-uris '("/bertramka" "/riegerovy-sady" "/klamovka" "/letna"
                                      "/stromovka" "/ladronka"
                                      "/frantiskanska-zahrada" "/kampa"
                                      "/obora-hvezda" "/petrin"
                                      "/kinskeho-zahrada" "/vysehrad"))

(defn set-park!
  [route-park-name]
  (when-not (= route-park-name @park/park-name)
    (dosync (ref-set park/park-name route-park-name))))

(defn routes-handler
  [uri params session]
  ; handle routes
  (cond
    (= uri "/")
    (response (index))

    (some #(= uri %) list-of-park-uris)
    (do
      (set-park! (str/replace uri #"/" ""))
      (when (get params "input")
        (dosync
          (ref-set chat-map
                   (assoc @chat-map
                          (keyword (str (:id session)))
                          (str
                            (get @chat-map
                                 (keyword
                                   (str (:id session))))
                            "<p class=\"human-msg\">"
                            (get params "input") "</p>"))))
        (chatbot-respond! session (get params "input")))
        (response (chatbot-page (str
                                    (get @chat-map
                                         (keyword (str (:id session))))) uri)))
    :else
    (response (str "<h1>Route not found</h1>"
                       "<h2><a href="\/">Home</a></h2>"))))

(defn ensure-session-id
  [session]
  (if (nil? (:id session))
    (loop [id (rand-int 100000000)]
      ; check if the id already exists
      (if-not (get @chat-map (keyword (str id)))
        (assoc session :id id)
        (recur (rand-int 100000000))))
      session))

(def request-handler
  (-> (fn [& args]
        (let [session (get (first args) :session)
              uri (get (first args) :uri)
              params (get (first args) :params)
              session+id (ensure-session-id session)
              server-response (routes-handler uri params session)]
      (-> server-response
          (assoc :session session+id))))
      (wrap-resource "public")
      (wrap-params {:encoding "UTF-8"})
      (wrap-session {:cookie-attrs {:max-age 3600}})))

(defn run-backend!
  []
  (run-jetty request-handler {:port 3000}))
