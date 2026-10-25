# 임대

다한오에이 임대프린터 목록을 관리하는 Spring Boot + React 프로젝트입니다.

- 서비스: `http://sarangmc.kr/rental/` 및 VIP `192.168.1.76:3018/rental/`
- 백엔드: 1.77·1.78의 `SRGH_IT-rental.service`, 각 3018 포트
- DB: MySQL 3307 `srghrental`; 쓰기 가능한 프라이머리는 VIP `192.168.1.76`
- 시트/테이블: `사용` → `in_use`, `회수` → `recovered`
- 원본 행 번호를 `source_row`로 보존했습니다. `사용` 시트의 총계 행은 장비 레코드가 아니므로 적재하지 않았습니다.
- 회수·사용 버튼은 모든 필드를 유지해 상대 테이블로 옮깁니다. 이동한 행에는 새 `source_row`가 자동 부여됩니다.
- 열: 품명→`item_name`, IP→`ip_address`, MAC→`mac_address`, 모델→`model`, 규격→`specification`, 단가→`unit_price`, 위치→`location`, 위치(세부)→`location_detail`, 비고→`remarks`, 계약일→`contract_date`.

로컬 빌드: `frontend`에서 `npm ci`, `npm run build` 후 프로젝트 루트에서 `mvn test package`를 실행합니다. 엑셀 재적재용 생성기는 `deploy/build_import_sql.py`에 있지만, 현재 운영 DB에 그대로 재실행하면 기존 DB 생성 단계에서 중단됩니다. 기존 데이터를 덮어쓰지 않도록 의도한 동작입니다.
