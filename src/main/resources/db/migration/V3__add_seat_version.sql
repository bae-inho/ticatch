-- 좌석 선점을 낙관적 락으로 비교 측정하기 위한 버전 열. 좌석이 바뀔 때마다 1씩 늘어난다.
ALTER TABLE seat ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
