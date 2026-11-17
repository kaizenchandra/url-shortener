package com.example.urlshortener.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Component
public class PayloadLimit extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
        if (!req.getRequestURI().startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }
        if (req.getContentLengthLong() > 16384) {
            res.sendError(413);
            return;
        }
        byte[] bytes = req.getInputStream().readNBytes(16385);
        if (bytes.length > 16384) {
            res.sendError(413);
            return;
        }
        chain.doFilter(new HttpServletRequestWrapper(req) {
            public ServletInputStream getInputStream() {
                var in = new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    public int read() {
                        return in.read();
                    }

                    public boolean isFinished() {
                        return in.available() == 0;
                    }

                    public boolean isReady() {
                        return true;
                    }

                    public void setReadListener(ReadListener l) {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        }, res);
    }
}
