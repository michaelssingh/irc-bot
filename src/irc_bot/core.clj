(ns irc-bot.core
  (:require [clojure.java.io :as io])
  (:import (javax.net.ssl SSLSocketFactory)
           (java.net Socket SocketException)))


(def servers [{:hostname "irc.hashbang.sh"
               :port     6697
               :tls      true
               :channels ["#!" "#bots"]}
              {:hostname "testnet.ergo.chat"
               :port     6667
               :tls      false
               :channels ["#bots" "#0"]}
              {:hostname "irc.tilde.chat"
               :port     6697
               :tls      true
               :channels ["#bots" "#meta"]}
              {:hostname "irc.libera.chat"
               :port     6667
               :tls      false
               :channels ["#bots" "#clojure"]}])

(def user {:username "Clojure Bot"
           :nick     "cljpt"})

(defn socket->ssl-socket
  [socket hostname port]
  (.createSocket
   (SSLSocketFactory/getDefault)
   socket
   hostname port true))

(defn conn->address-str [conn]
  (-> conn
      :socket
      .getRemoteSocketAddress
      .toString))

(defn disconnect [connection-atoms]
  (doseq [conn* connection-atoms]
    (if (->  @conn* :socket .isClosed)
      (println "No established connection to:"
               (conn->address-str @conn*))
      (do
        (println "Closing TCP socket connection:"
                 (conn->address-str @conn*))
        (-> @conn* :socket .close)))))


(defn write [{:keys [writer] :as _conn} msg]
  (.write writer (str msg "\n"))
  (.flush writer))

(defn join [conn chan]
  (write conn (str "JOIN " chan)))

(defn join-all-channels [connection-atoms]
    (doseq [conn* connection-atoms
          ch (:channels @conn*)]
      (join @conn* ch))
  connection-atoms)

(defn login [conn*
             {:keys [nick username]
              :as _user}]
  (println "logging " username " into "
           (conn->address-str @conn*))
  (write @conn* (str "NICK " nick))
  (write @conn* (str "USER " nick " 0 * :" username))
  conn*)

(defn read-messages [conn*]
  (with-open [socket (:socket @conn*)
              reader (io/reader socket)]
        (swap! conn* assoc :reader reader)
        (doseq [line (line-seq reader)]
          (cond
            (re-find #"^ERROR :Closing Link:" line)
            (swap! conn* merge {:exit true})

            (re-find #"^PING" line)
            (write conn* (str "PONG " (re-find #":.*" line))))

          ;else
          (println line))))

(defn spawn-message-reader [conn*]
  (future
    (try
      (read-messages conn*)
      (catch SocketException e
        (println "Exception : "
                 (conn->address-str conn*)
                 " : " e))))
  conn*)

(defn server->connection
  [{:keys [port tls hostname]
    :as server}]
  (let [socket (cond-> (Socket. hostname port)
                 tls
                 (socket->ssl-socket hostname port))]
    (atom
     (merge server
            {:socket socket
             :writer (io/writer socket :append true)}))))

(defn connect-and-login-all-servers []
  (let [connection-atoms (map server->connection servers)]
    (doseq [c connection-atoms]
      (-> c
          (login user)
          spawn-message-reader))
    connection-atoms))

(comment

  (def connections-atoms
    (connect-and-login-all-servers))
  
  (disconnect connections-atoms)

  (join-all-channels connections-atoms)

  ,)
