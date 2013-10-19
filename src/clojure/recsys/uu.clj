(ns recsys.uu
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [parenskit (vector :as lkv) (core :as lkc)]
            [esfj.provider :refer [defprovider]])
  (:import [java.util Collection]
           [org.grouplens.lenskit ItemScorer]
           [org.grouplens.lenskit.data.dao EventDAO ItemDAO UserDAO]
           [org.grouplens.lenskit.data.dao UserEventDAO ItemEventDAO]
           [org.grouplens.lenskit.data.event Rating]
           [org.grouplens.lenskit.data.history
             History RatingVectorUserHistorySummarizer UserHistory]
           [org.grouplens.lenskit.vectors SparseVector]
           [org.grouplens.lenskit.vectors MutableSparseVector]
           [recsys.dao ItemTitleDAO MOOCRatingDAO MOOCItemDAO MOOCUserDAO]
           [recsys.dao RatingFile TitleFile UserFile]))

(defn get-uvec
  [^UserEventDAO udao ^long user]
  (->> (or (.getEventsForUser udao user Rating) (History/forUser user))
       (RatingVectorUserHistorySummarizer/makeRatingVector)))

(defprovider suu-item-scorer
  ^ItemScorer [^UserEventDAO udao ^ItemEventDAO idao]
  (lkc/item-scorer
   (fn score [^long user ^MutableSparseVector scores]
     (let [uvec (get-uvec udao user)]
       ;; TODO Score items for this user using user-user collaborative
       ;;      filtering
       (lkv/map-keys!
        (fn [item]
          ;; This is the function to iterate over items to score
          0.0)
        :either scores)))))

(defn configure-recommender
  []
  (doto (lkc/config)
    (-> (.bind EventDAO) (.to MOOCRatingDAO))
    (-> (.set RatingFile) (.to (io/file "data/ratings.csv")))
    (-> (.bind ItemDAO) (.to MOOCItemDAO))
    (-> (.set TitleFile) (.to (io/file "data/movie-titles.csv")))
    (-> (.bind UserDAO) (.to MOOCUserDAO))
    (-> (.set UserFile) (.to (io/file "data/users.csv")))
    (-> (.bind ItemScorer) (.toProvider suu-item-scorer))))

(defn parse-args
  [args]
  (reduce (fn [to-score arg]
            (let [[uid iid] (map #(Long/parseLong %) (str/split arg #":"))]
              (update-in to-score [uid] (fnil conj #{}) iid)))
          {} args))

(defn -main
  [& args]
  (let [to-score (parse-args args)
        config (configure-recommender)
        rec (lkc/rec-build config)
        scorer (.getItemScorer rec)
        tdao (lkc/rec-get rec ItemTitleDAO)]
    (doseq [[^Long user ^Collection items] to-score
            :let [_ (log/info "scoring" (count items) "items for user" user)
                  scores (.score scorer user items)]
            ^Long item items
            :let [score (if-not (.containsKey scores item)
                          "NA"
                          (format "%.4f" (.get scores item)))
                  title (.getItemTitle tdao item)]]
      (printf "%d,%d,%s,%s\n" user item score title))))
