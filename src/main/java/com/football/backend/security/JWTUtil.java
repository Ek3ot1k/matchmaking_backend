package com.football.backend.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.Date;

@Component
public class JWTUtil {

    // Секретный ключ бери из application.properties (например, jwt.secret=mySuperSecretKey...)
    @Value("${jwt_secret}")
    private String secret;

    public String generateToken(Long userId) {
        return JWT.create()
                .withSubject("User details")
                .withClaim("userId", userId)
                .withIssuedAt(new Date())
                .withIssuer("football-matchmaker")
                .withExpiresAt(Date.from(
                        ZonedDateTime.now().plusHours(10).toInstant()
                ))
                .sign(Algorithm.HMAC256(secret));
    }

    public Long validateTokenAndRetrieveClaim(String token)
            throws JWTVerificationException {

        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(secret))
                .withSubject("User details")
                .withIssuer("football-matchmaker")
                .build();

        DecodedJWT jwt = verifier.verify(token);

        Long userId = jwt.getClaim("userId").asLong();
        if (userId == null) {
            throw new JWTVerificationException("Missing userId claim");
        }

        return userId;
    }
}