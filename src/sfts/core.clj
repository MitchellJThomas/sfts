(ns sfts.core
  (:require
   [org.httpkit.client :as http]
   [clj-time.core :as t]
   [clj-time.coerce :as tc]
   [clj-time.format :as tf]
   [hickory.select :as hics]
   [hickory.core :as hic])
  (:gen-class))

(def friday-show-start (t/date-time 2014 9 19 23))
(def monday-show-start (t/date-time 2014 3 17 21))
(def fm (tf/formatter "YYYY-MM-dd_HH"))

(defn download-uri
  [dt]
  (format "https://xray-fc1-west.creek.fm/audio/searchingforthesound/searchingforthesound_%s-00-00.mp3" (tf/unparse fm dt)))

(defn downloads
  ([start-date]
   (downloads start-date (t/now)))
  ([start-date end-date]
   (let [num-shows (t/in-weeks (t/interval start-date end-date))
         shows (range num-shows)]
     (for [s shows]
       (download-uri (t/plus start-date (t/weeks s)))))))

(defn build-downloads
  []
  (let [d (clojure.java.io/file "download.sh")
        d2 (clojure.java.io/file "download2.sh")]
    (dorun (map #(spit d (format "wget -nc %s\n" %) :append true) (downloads friday-show-start)))
    (dorun (map #(spit d2 (format "wget -nc %s\n" %) :append true) (downloads monday-show-start friday-show-start)))))

(defn -main
  "Downloading Searching For The Sound Shows"
  [& args]  
  (build-downloads))

(comment
  ;; Download bytes
  (def b (:body @(http/get "https://xray-fc1-west.creek.fm/audio/searchingforthesound/searchingforthesound_2015-06-26_23-00-00.mp3" {:as :stream})))
  (def i (clojure.java.io/input-stream b))
  (clojure.java.io/copy i (clojure.java.io/file "stuff"))

  ;; Parse all show titles and show urls
  (def ht (-> @(http/get "http://xray.fm/programs/searchingforthesound/page:9?url=shows%2Fsearchingforthesound")
              :body
              hic/parse
              hic/as-hickory))
  (def show-map (hics/select
              (hics/descendant (hics/class :broadcast) (hics/class :title) (hics/attr :href #(.startsWith % "/broadcasts/"))) ht))
  (def shows (map (fn [s] [(first  (:content s)) (get-in s [:attrs :href])] ) show-map))
  shows

  ;; Parse a given show
  (def sho (-> @(http/get "http://xray.fm/broadcasts/525")
              :body
              hic/parse
              hic/as-hickory))
  (def show-tracks (hics/select (hics/descendant (hics/class :tracks-container) (hics/class :track)) sho))
  (def show-track-map  (for [t (->> show-tracks
                                    (map (fn [{c :content}] c))
                                    (map #(filter map? %)))]
                         (clojure.string/join ", " (map (fn [{{track :class} :attrs con :content}] (format "%s: %s" (re-find #"\w+$" track) (first con))) t))))
  show-track-map


  )
