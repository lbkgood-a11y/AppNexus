from pathlib import Path
import re
source=Path('.vite/extract_master_literals.py').read_text(encoding='utf-8')
source=source.replace("root=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/product')","root=Path('backend/services/print-service/adapters/jdbc/src/main/java/com/appnexus/printing/adapter/jdbc')")
source=re.sub(r"for name in \['ProductV1Service'.*?\]:", "for name in ['PrintRuntime','PrintFlow','PrintOutputs']:",source)
source=source.replace("out=Path('backend/services/master-data-service/src/main/java/com/appnexus/masterdata/adapter/jdbc')/(mapper+'.java')","out=root/(mapper+'.java')")
source=source.replace("'package com.appnexus.masterdata.product;'","'package com.appnexus.printing.adapter.jdbc;'").replace("'package com.appnexus.masterdata.product;\\nimport com.appnexus.masterdata.adapter.jdbc.'","'package com.appnexus.printing.adapter.jdbc;\\nimport com.appnexus.printing.adapter.jdbc.'").replace('package com.appnexus.masterdata.adapter.jdbc;','package com.appnexus.printing.adapter.jdbc;')
exec(source)
