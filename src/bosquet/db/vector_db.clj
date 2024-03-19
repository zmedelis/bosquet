(ns bosquet.db.vector-db)

(defprotocol VectorDB
  (create [_this])
  (delete [_this])
  (add [_this _docs])
  (search [_this _query _search-opts]))
