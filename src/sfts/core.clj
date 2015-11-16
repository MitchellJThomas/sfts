(ns sfts.core
  (:require
   [clojure.java.io :as io]
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
(def fm (tf/formatter-local "YYYY-MM-dd_HH"))
(def album-fm (tf/formatter-local "YYYY-MM-dd"))
;; (def grouping-fm (tf/formatter-local "MMMM dd"))
(def bcast-fm (tf/formatter-local "hh:mmaa, MM-dd-YYYY"))

(defn mp3-broadcast-file
  [dt]
  (format "searchingforthesound_%s-00-00.mp3" (tf/unparse fm dt)))

(defn txt-broadcast-file
  [dt]
  (format "searchingforthesound_%s-00-00.txt" (tf/unparse fm dt)))

(defn mp3-download-uri
  [dt]
  (str "https://xray-fc1-west.creek.fm/audio/searchingforthesound/" (mp3-broadcast-file dt)))

(defn get-mp3-downloads
  "Build urls for all the downloads given a particular start date"
  ([start-date]
   (get-mp3-downloads start-date (t/now)))
  ([start-date end-date]
   (let [num-shows (t/in-weeks (t/interval start-date end-date))
         shows (range num-shows)]
     (for [s shows]
       (mp3-download-uri (t/plus start-date (t/weeks s)))))))

(defn download-mp3-broadcast
  "Download the broadcast corresponding to the provide date to the provided directory"
  [dt dir]
  (let [{:keys [body status]} @(http/get (mp3-download-uri dt) {:as :stream})]
    (when (= 200 status)
      (let [mp3-stream (io/input-stream body)
            download-file (io/file (str dir (mp3-broadcast-file dt)))]
        (io/copy mp3-stream download-file))
      true)))

(defn get-broadcasts
  "Get all the broadcasts including specific broadcast url and title"
  [broadcasts-url]
  (let [{:keys [body status]} @(http/get broadcasts-url)]
    (when (= 200 status)
      (->> body
           hic/parse
           hic/as-hickory
           (hics/select (hics/descendant (hics/class :broadcast)
                                         (hics/or (hics/child (hics/class :title)
                                                              (hics/attr :href #(.startsWith % "/broadcasts/")))
                                                  (hics/class :date))))
           (partition 2)
           (map (fn [[{[show-date] :content} { {show-href :href} :attrs [show-title] :content}]]
                  {:date (tf/parse bcast-fm show-date) :href show-href :title show-title}))))))

;; lets be nice to the xray webserver
(def get-broadcasts-memo (memoize get-broadcasts))

(defn get-broadcast-deets
  "Parse a given show's web page"
  [broadcast-url]
  (let [{:keys [body status]} @(http/get broadcast-url)]
    (when [(= 200 status)]
      (->> body
           hic/parse
           hic/as-hickory
           (hics/select (hics/descendant (hics/class :tracks-container) (hics/class :track)))))))

(def get-broadcast-deets-memo (memoize get-broadcast-deets))

;; TODO convert to using transducers
(defn clean-broadcast-deets
  [broadcast-deets]
  (let [tracks (->> broadcast-deets
                    (map (fn [{c :content}] c))
                    (map #(filter map? %)))]
    (map #(map (fn [{{track :class} :attrs con :content}]
                 (format "%s: %s"  (re-find #"\w+$" track) (first con))) %) tracks)))

(defn get-all-broadcasts
  "Get all the broadcasts and related details that XRAY has for Searching For The Sound"
  []
  (let [n (range 1 10)
        show-urls (map #(format "http://xray.fm/programs/searchingforthesound/page:%s?url=shows%%2Fsearchingforthesound" %) n)]
    (flatten (remove nil? (map get-broadcasts-memo show-urls)))))

(defn get-broadcast-notes
  [broadcast-url]
  (->> broadcast-url
       get-broadcast-deets-memo
       (remove nil?)
       clean-broadcast-deets
       (map #(clojure.string/join ", " %))))

(defn get-all-broadcasts-with-notes
  []
  (let [bcast-fn (fn [bcast]
                   (assoc bcast :tracks (get-broadcast-notes (str "http://xray.fm" (:href bcast)))))]
    (map bcast-fn (get-all-broadcasts))))

(defn map-show-to-file
  [show]
  (format "Searching For The Sound\n%s\n%s\nDJ Cozmic Edward\nhttp://xray.fm/shows/searchingforthesound\n\ntracks:\n%s"
       (:title show)
       (tf/unparse bcast-fm  (:date show))
       (clojure.string/join \newline (:tracks show))))

(defn map-show-to-mp3
  [show]
  {
   ;; :grouping (tf/unparse grouping-fm (:date show))
   :lyrics  (clojure.string/join \newline (:tracks show))
   :artist  (identity  "Searching for the Sound")
   :album   (str (tf/unparse album-fm (:date show))) 
   :title  (:title show)
   :year   (str (t/year (:date show)))
   :composer  (identity  "DJ Cozmic Edward")
   :comment  (identity "http://xray.fm/shows/searchingforthesound")
   :is-compilation true
   :genre "Spoken & Audio"
   :artwork-file (.getFile (io/resource "cover.jpg"))})

(defn needs-mp3-tags?
  "Does the provided mp3 file need tags?"
  [mp3-file]
  (try
    (let [{:keys [composer comment]} (mp3/get-all-info mp3-file)]
      (and (not= composer "DJ Cozmic Edward")
           (not= comment "http://xray.fm/shows/searchingforthesound")))
    (catch Exception e (println "Failed to get tag info for" mp3-file))))

(defn build-downloads-script
  "Build a file using wget for downloading all the shows using dates"
  []
  (let [d (io/file "download.sh")
        d2 (io/file "download2.sh")]
    (dorun (map #(spit d (format "wget -nc %s\n" %) :append true) (get-mp3-downloads friday-show-start)))
    (dorun (map #(spit d2 (format "wget -nc %s\n" %) :append true) (get-mp3-downloads monday-show-start friday-show-start)))))

(defn whole-shebang!
  "Download, tag and write details for all shows to the provided directory"
  [dir]
  (doseq [b (get-all-broadcasts-with-notes)]
    (let [mp3 (io/file (str dir (mp3-broadcast-file (:date b))))
          txt (io/file (str dir (txt-broadcast-file (:date b))))
          dt (tf/unparse fm (:date b))]
      (if (not (.exists mp3))
        (if (download-mp3-broadcast (:date b) dir)
          (println "Downloaded broadcast" (str mp3))))
      (when (and (.exists mp3) (needs-mp3-tags? (str mp3)))
        (try
          (mp3/update-tag! mp3 (map-show-to-mp3 b))
          (println "Tagged broadcast" (str mp3))
          (catch Exception e (println "Got error" e "tagging broadcast" mp3))))
      (spit txt (map-show-to-file b))
      (println "Wrote broadcast details for" dt))))

(defn -main
  "Downloading all Searching For The Sound shows (an XRAY.fm broadcast)"
  [& args]  
  (whole-shebang! "./"))

(comment
  (get-broadcasts-memo "http://xray.fm/programs/searchingforthesound/page:8?url=shows%2Fsearchingforthesound")

  (def bn (get-all-broadcasts-with-notes))
  (map-show-to-mp3 (nth bn 3))
  (map-show-to-file (nth bn 3))
  
  ;; get the mp3 infos, put the mp3 infos
  (mp3/get-all-info "/Users/mthomas/Downloads/searching_for_the_sound/searchingforthesound_2015-10-23_23-00-00.mp3")

  (download-mp3-broadcast (:date (nth bn 50)) "/Users/mthomas/Downloads/searching_for_the_sound/")

  (whole-shebang! "/Users/mthomas/Downloads/searching_for_the_sound/")

  )
