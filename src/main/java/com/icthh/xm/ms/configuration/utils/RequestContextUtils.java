package com.icthh.xm.ms.configuration.utils;

import com.icthh.xm.commons.request.XmRequestContextHolder;
import com.icthh.xm.ms.configuration.config.RequestContextKeys;
import com.icthh.xm.ms.configuration.domain.RequestSourceType;
import lombok.experimental.UtilityClass;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * The {@link RequestContextUtils} class.
 */
@UtilityClass
public class RequestContextUtils {

    public static final String OLD_CONFIG_HASH = "oldConfigHash";

    public static Optional<RequestSourceType> getRequestSourceType(XmRequestContextHolder holder) {
        RequestSourceType value = holder.getContext().getValue(RequestContextKeys.REQUEST_SOURCE_TYPE,
                                                               RequestSourceType.class);
        return Optional.ofNullable(value);
    }

    public static String getRequestSourceTypeLogName(XmRequestContextHolder holder) {
        Optional<RequestSourceType> requestSourceType = getRequestSourceType(holder);
        return requestSourceType.isPresent() ? requestSourceType.get().getName() : "unknown";
    }

    public static boolean isRequestSourceTypeExist(XmRequestContextHolder holder) {
        return getRequestSourceType(holder).isPresent();
    }

    public static Optional<String> getRequestSourceName(XmRequestContextHolder holder) {
        String value = holder.getContext().getValue(RequestContextKeys.REQUEST_SOURCE_NAME,
                                                    String.class);
        return Optional.ofNullable(value);
    }

    public static String getRequestSourceLogName(XmRequestContextHolder holder) {
        Optional<String> requestSourceName = getRequestSourceName(holder);
        return requestSourceName.orElse("unknown");
    }

    public static boolean isRequestSourceNameExist(XmRequestContextHolder holder) {
        return getRequestSourceName(holder).isPresent();
    }

    public static boolean getBooleanParameter(HttpServletRequest request, String paramName) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap.containsKey(paramName) && parameterMap.get("processed").length > 0) {
            return Boolean.parseBoolean(parameterMap.get("processed")[0]);
        }
        return false;
    }

}
