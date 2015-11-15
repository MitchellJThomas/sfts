(ns sfts.core
  (:require
   [org.httpkit.client :as http]
   [clj-time.core :as t]
   [clj-time.coerce :as tc]
   [clj-time.format :as tf]
   [hickory.select :as hics]
   [hickory.core :as hic]
   [green-tags.core :as mp3])
  (:gen-class))

(def friday-show-start (t/date-time 2014 9 19 23))
(def monday-show-start (t/date-time 2014 3 17 21))
(def fm (tf/formatter "YYYY-MM-dd_HH"))
(def album-fm (tf/formatter "YYYY-MM-dd"))
(def grouping-fm (tf/formatter "MMMM dd"))
(def bcast-fm (tf/formatter-local "hh:mmaa, MM-dd-YYYY"))

(defn mp3-download-uri
  [dt]
  (format "https://xray-fc1-west.creek.fm/audio/searchingforthesound/searchingforthesound_%s-00-00.mp3" (tf/unparse fm dt)))

(defn get-mp3-downloads
  ([start-date]
   (get-mp3-downloads start-date (t/now)))
  ([start-date end-date]
   (let [num-shows (t/in-weeks (t/interval start-date end-date))
         shows (range num-shows)]
     (for [s shows]
       (mp3-download-uri (t/plus start-date (t/weeks s)))))))

(defn build-downloads
  []
  (let [d (clojure.java.io/file "download.sh")
        d2 (clojure.java.io/file "download2.sh")]
    (dorun (map #(spit d (format "wget -nc %s\n" %) :append true) (get-mp3-downloads friday-show-start)))
    (dorun (map #(spit d2 (format "wget -nc %s\n" %) :append true) (get-mp3-downloads monday-show-start friday-show-start)))))

(defn get-broadcasts
  "Get all the broadcasts including specific broadcast url and title"
  [broadcasts-url]
  (->> @(http/get broadcasts-url)
       :body
       hic/parse
       hic/as-hickory
       (hics/select (hics/descendant (hics/class :broadcast)
                                     (hics/or (hics/child (hics/class :title)
                                                          (hics/attr :href #(.startsWith % "/broadcasts/")))
                                              (hics/class :date))))
       (partition 2)
       (map (fn [[{[show-date] :content} { {show-href :href} :attrs [show-title] :content}]]
              {:date (tf/parse bcast-fm show-date) :href show-href :title show-title}))))

;; lets be nice to the xray webserver
(def get-broadcasts-memo (memoize get-broadcasts))

(defn get-broadcast-deets
  "Parse a given show's web page"
  [broadcast-url]
  (->> @(http/get broadcast-url)
       :body
       hic/parse
       hic/as-hickory
       (hics/select (hics/descendant (hics/class :tracks-container) (hics/class :track)))))

(def get-broadcast-deets-memo (memoize get-broadcast-deets))

;; TODO convert to using transducers
(defn clean-broadcast-deets
  [broadcast-deets]
  (let [tracks (->> broadcast-deets
                    (map (fn [{c :content}] c))
                    (map #(filter map? %)))]
    (map #(map (fn [{{track :class} :attrs con :content}]
                 (format "%s: %s"  (re-find #"\w+$" track)(first con))) %) tracks)))

(defn get-broadcast-notes
  [broadcast-url]
  (->> broadcast-url
       get-broadcast-deets-memo
       clean-broadcast-deets
       (map #(clojure.string/join ", " %))
       ))

(defn get-all-broadcasts
  "Get all the broadcasts and related details that XRAY has for Searching For The Sound"
  []
  (let [n (range 1 10)
        show-urls (map #(format "http://xray.fm/programs/searchingforthesound/page:%s?url=shows%%2Fsearchingforthesound" %) n)]
    (flatten (map get-broadcasts-memo show-urls))))

(defn get-all-broadcasts-with-notes
  []
  (map #(assoc % :tracks (get-broadcast-notes (str "http://xray.fm" (:href %)))) (get-all-broadcasts)))

(defn -main
  "Downloading Searching For The Sound Shows"
  [& args]  
  (build-downloads))

(comment
  ;; Download bytes
  (def b (:body @(http/get "https://xray-fc1-west.creek.fm/audio/searchingforthesound/searchingforthesound_2015-06-26_23-00-00.mp3" {:as :stream})))
  (def i (clojure.java.io/input-stream b))
  (clojure.java.io/copy i (clojure.java.io/file "stuff"))

  (get-broadcasts-memo "http://xray.fm/programs/searchingforthesound/page:9?url=shows%2Fsearchingforthesound")
  (def bn (get-all-broadcasts-with-notes))
  bn

  (def fe (mp3/get-all-info "/Users/mthomas/Music/iTunes/iTunes Media/Music/Compilations/2015-10-23/Soul Sisters.mp3"))
  fe

    (defn map-it-to-mp3
    [show]
    {:grouping (tf/unparse grouping-fm (:date show))
     :lyrics  (clojure.string/join \newline (:tracks show))
     :artist  (identity  "Searching for the Sound")
     :album   (str (tf/unparse album-fm (:date show))) 
     :title  (:title show)
     :year   (t/year (:date show))
     :composer  (identity  "DJ Cozmic Edward")
     :comment  (identity  "http://xray.fm/shows/searchingforthesound") }
      )

    (defn map-it-to-file
      [show]
      (printf "Searching For The Sound\n%s\n%s\nDJ Cozmic Edward\nhttp://xray.fm/shows/searchingforthesound\n\ntracks:\n%s\n\n"
              (:title show)
              (:date show)
              (clojure.string/join \newline (:tracks show))))

  (map-it-to-mp3 (first bn))
  (map-it-to-file (first bn))
  
  
  ;; get the mp3 infos, put the mp3 infos
  (mp3/get-all-info "/Users/mthomas/Music/iTunes/iTunes Media/Music/Searching For The Sound/Unknown Album/searchingforthesound_2015-10-30_23-00-00.mp3")
  (mp3/update-tag! "/Users/mthomas/Music/iTunes/iTunes Media/Music/Searching For The Sound/Unknown Album/searchingforthesound_2015-10-30_23-00-00.mp3" { :year "2015", :composer "DJ Cozmic Edward", :comment "http://xray.fm/shows/searchingforthesound"})

  (clojure.string/join "\n"(get-broadcast-notes "http://xray.fm/broadcasts/9382"))

  (tf/unparse bcast-fm (t/now))


  )
