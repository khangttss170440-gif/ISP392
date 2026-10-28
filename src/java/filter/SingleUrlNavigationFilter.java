package filter;

import java.io.IOException;
import java.util.Locale;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.AppConfig;

/** Keep browser page navigation inside one same-origin application frame. */
public final class SingleUrlNavigationFilter implements Filter {
    @Override
    public void doFilter(ServletRequest input, ServletResponse output, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) input;
        HttpServletResponse response = (HttpServletResponse) output;
        String destination = request.getHeader("Sec-Fetch-Dest");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String accept = request.getHeader("Accept");
        boolean browserPage = "document".equals(destination)
                || (destination == null && accept != null
                    && accept.toLowerCase(Locale.ROOT).contains("text/html"));
        boolean home = path.isEmpty() || "/".equals(path);
        // Frame navigations, form POSTs, fetch/JSON, assets and provider IPNs
        // must reach their original controllers without an HTML wrapper.
        if (!AppConfig.bool("APP_SINGLE_URL_ENABLED") || !"GET".equals(request.getMethod())
                || "iframe".equals(destination) || (!browserPage && !home)
                || path.startsWith("/assets/") || "/product-image".equals(path)
                || path.endsWith("-ipn") || path.startsWith("/WEB-INF/")) {
            chain.doFilter(request, response);
            return;
        }
        String page = home ? request.getContextPath() + "/index.jsp" : request.getRequestURI();
        if (request.getQueryString() != null) page += "?" + request.getQueryString();
        request.setAttribute("navigationPage", page);
        response.setHeader("Cache-Control", "no-store");
        request.getRequestDispatcher("/WEB-INF/views/app-shell.jsp").forward(request, response);
    }
}
