-- 측정하는 동안 0.2초마다 앱의 DB 연결을 센다. run.sh가 백그라운드로 띄우고, 측정이 끝나면 취소한다.
-- busy: 쿼리를 실행 중이거나 트랜잭션 안에 있는 연결, lock: 그중 행 잠금을 기다리는 연결.
SET application_name = 'perf-waits';

DO
$$
    DECLARE
        busy integer;
        lock integer;
    BEGIN
        LOOP
            PERFORM pg_stat_clear_snapshot();
            SELECT count(*) FILTER (WHERE state <> 'idle'),
                   count(*) FILTER (WHERE wait_event_type = 'Lock')
            INTO busy, lock
            FROM pg_stat_activity
            WHERE datname = 'ticatch_perf'
              AND application_name = 'PostgreSQL JDBC Driver';
            RAISE NOTICE 'sample busy=% lock=%', busy, lock;
            PERFORM pg_sleep(0.2);
        END LOOP;
    END
$$;
