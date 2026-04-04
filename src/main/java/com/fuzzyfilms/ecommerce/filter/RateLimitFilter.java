package com.fuzzyfilms.ecommerce.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Long>> requests = new ConcurrentHashMap<>();
    private static final int MAX_REQUESTS_PER_MINUTE = 30;
    private static final long TIME_WINDOW_MS = 60_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        // Aplica apenas em /login/auth e /cadastro
        if (!"/login/auth".equals(path) && !"/cadastro".equals(path) && !"/".equals(path) && !"/login/2fa".equals(path) && !"/login".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        ConcurrentLinkedDeque<Long> timestamps = requests.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            long now = System.currentTimeMillis();
            while (!timestamps.isEmpty() && timestamps.peekFirst() < now - TIME_WINDOW_MS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
    response.sendError(429, "Too Many Requests");
    return;
}
            timestamps.addLast(now);
        }

        chain.doFilter(request, response);
    }
}