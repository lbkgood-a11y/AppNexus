package com.appnexus.printing.adapter.web;

import com.appnexus.contracts.businessobject.BusinessObjects.Rejection;
import com.appnexus.contracts.printing.PrintRejection;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;

/** 公共打印命令复用专用入口的稳定失败码，不回显存储或业务原文。 */
final class PrintCommandSupport {
    private PrintCommandSupport(){}

    static Map<String,Object> result(Supplier<?> command){
        try{return Map.of("result",command.get());}
        catch(PrintRejection error){throw new Rejection(error.status(),error.code());}
        catch(DuplicateKeyException error){throw new Rejection(409,"PRINT_DUPLICATE");}
        catch(DataAccessException error){throw new Rejection(503,"PRINT_STORAGE_UNAVAILABLE");}
        catch(IllegalArgumentException|NullPointerException error){throw new Rejection(400,"PRINT_INPUT_INVALID");}
    }
}
