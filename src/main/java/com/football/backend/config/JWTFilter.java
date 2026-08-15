package com.football.backend.config;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.football.backend.security.JWTUtil;
import com.football.backend.service.UserEntityDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JWTFilter extends OncePerRequestFilter {
    private final UserEntityDetailsService userEntityDetailsService;
    private final JWTUtil jwtUtil;

    public JWTFilter(UserEntityDetailsService userEntityDetailsService, JWTUtil jwtUtil) {
        this.userEntityDetailsService = userEntityDetailsService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String authHeader=request.getHeader("Authorization");

        if(authHeader!=null && authHeader.startsWith("Bearer ")){
            String jwt=authHeader.substring(7);

            if (jwt.isBlank()) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid JWT Token");
                return;
            }

            try {
                Long id = jwtUtil.validateTokenAndRetrieveClaim(jwt);

                UserDetails userDetails = userEntityDetailsService.loadUserById(id);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                SecurityContextHolder.getContext()
                        .setAuthentication(authToken);
            }catch (JWTVerificationException | UsernameNotFoundException ex) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Invalid JWT Token");
                return;
            }
        }
        filterChain.doFilter(request,response);

    }

}

