(ns dk.cst.hjelmslev.timeline
  "Wrapper for the Simile Timeline widget. Depends on some ancient JS libs."
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(defn prepare-event
  "Create an event for a timeline event source based on an `event` map.
  In most cases, :start is the only required key.

    :id          - An internal id. Really shouldn't be used by events.
                   Timeline library clients should use eventID
    :eventId     - For use by library client when writing custom painters or
                   custom fillInfoBubble
    :start       - Date string.
    :end         - Date string.
    :latestStart - Date string.
    :earliestEnd - Date string.
    :instant     - Boolean for (non-)precise logic and duration/instant issues.
    :title/:text - Used as the label on Timelines and in bubbles.
    :description - Used in bubbles.
    :image       - Used in bubbles.
    :link        - Used in bubbles.
    :icon        - The icon shown on the Timeline.
    :color       - Timeline label and tape color.
    :textColor   - Timeline label color, overrides color attribute
    :hoverText   - [DEPRECATED] Superseded by caption.
    :caption     - The HTML title attribute of the event.
    :classname   - The CSS class of the event on the Timeline.
    :tapeImage   - Sets the background image of the duration event's tape div.
    :tapeRepeat  - Repeat attribute for tapeImage (repeat, repeat-x, repeat-y)."
  [{:keys [isDuration durationEvent title start] :as event}]
  (let [date     (js/Date. start)
        defaults {:instant (not (or isDuration durationEvent))
                  :caption (str (.toLocaleDateString date) ": " title)}
        date-ks  #{:start :end :latestStart :earliestEnd}
        dates    (->> (filter (comp date-ks first) event)
                      (map (fn [[k v]]
                             (if (string? v)
                               [k (js/Date. v)]
                               [k v]))))]
    (-> (assoc event :text title)
        (merge defaults)
        (into dates))))

(defn add-events!
  "Add `events` to a Simile Timeline `event-source`."
  [^js/Timeline.DefaultEventSource event-source events]
  (let [->Event #(js/Timeline.DefaultEventSource.Event. (clj->js %))]
    (->> (map (comp ->Event prepare-event) events)
         (into-array)
         (.addMany event-source))))

(def ->DateTime
  "Mapping the interval time units described in labeller.js."
  {:millisecond js/SimileAjax.DateTime.MILLISECOND
   :second      js/SimileAjax.DateTime.SECOND
   :minute      js/SimileAjax.DateTime.MINUTE
   :hour        js/SimileAjax.DateTime.HOUR
   :day         js/SimileAjax.DateTime.DAY
   :week        js/SimileAjax.DateTime.WEEK
   :month       js/SimileAjax.DateTime.MONTH
   :year        js/SimileAjax.DateTime.YEAR
   :decade      js/SimileAjax.DateTime.DECADE
   :century     js/SimileAjax.DateTime.CENTURY
   :millennium  js/SimileAjax.DateTime.MILLENNIUM})

(def ->Layout
  "Mapping the layouts described in timeline.js."
  {:horizontal js/Timeline.HORIZONTAL
   :vertical   js/Timeline.VERTICAL})

(defn ->HotZoneBandInfos
  "Create two connected `bands` for a Simile Timeline `event-source`."
  [event-source {:keys [primary overview common] :as bands}]
  (let [get-unit #(get ->DateTime % %)
        primary  (-> (merge common primary)
                     (assoc :eventSource event-source)
                     (update :intervalUnit get-unit))
        overview (-> (merge common overview)
                     (assoc
                       :eventSource event-source
                       :layout "overview")
                     (update :intervalUnit get-unit))]
    (array (js/Timeline.createHotZoneBandInfo (clj->js primary))
           (doto (js/Timeline.createHotZoneBandInfo (clj->js overview))
             (set! -syncWith 0)
             (set! -highlight true)))))

(defn draw-timeline!
  "Draw a Simile Timeline in the HTML `element` based on `opts`."
  [element {:keys [layout events band-infos timeline]
            :or   {layout :horizontal}
            :as   opts}]
  (let [event-source (js/Timeline.DefaultEventSource.)
        band-infos   (->HotZoneBandInfos event-source band-infos)]
    (when-not timeline
      (js/Timeline.create element band-infos (->Layout layout)))
    (add-events! event-source events)))

(defn timeline
  "Display events inside a Simile Timeline based on HTML `attr` and `opts`.
  Note that in order for the component to display a height must be set!

  The available options are:

    :events     - events as Clojure maps (see the prepare-event fn).
    :band-infos - information about the bands (see the ->BandInfos fn).
    :layout     - (OPTIONAL) can be :horizontal or :vertical."
  [attr opts]
  (r/create-class
    {:component-did-mount
     (fn [this]
       (draw-timeline! (rdom/dom-node this) opts))

     :reagent-render
     (fn [attr opts]
       [:div attr])}))
