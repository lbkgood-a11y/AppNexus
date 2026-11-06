package com.appnexus.platform.security;
import com.appnexus.platform.identity.*;
import com.fasterxml.uuid.Generators;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
@Configuration(proxyBeanMethods = false)
public class PersistentAccounts {
    @Bean
    UserDetailsService accounts(AccountRepository repository) {
        return username -> {
            var account = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
            return User.withUsername(account.id().toString()).password(account.passwordHash())
                .disabled(!account.enabled()).authorities("ACCOUNT").build();
        };
    }
    @Bean
    @org.springframework.core.annotation.Order(0)
    ApplicationRunner initialAdministrator(AccountRepository repository, PasswordEncoder encoder,
            @Value("${app.bootstrap.username:}") String username,
            @Value("${app.bootstrap.password:}") String password) {
        return args -> {
            if (username.isBlank() && password.isBlank()) return;
            if (username.isBlank() || password.length() < 12)
                throw new IllegalArgumentException("Bootstrap requires a username and a password of at least 12 characters");
            repository.createInitialAdministrator(new AccountCredentials(
                Generators.timeBasedEpochGenerator().generate(), username, username,
                encoder.encode(password), true, 0));
        };
    }
}
