package com.crm.demo.controller;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.crm.demo.model.PasswordResetToken;
import com.crm.demo.model.User;
import com.crm.demo.repository.PasswordResetTokenRepository;
import com.crm.demo.repository.UserRepository;
@Controller

public class PasswordController {
	@Autowired
	private JavaMailSender mailSender;
	@Autowired
	private PasswordResetTokenRepository tokenRepository;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private PasswordEncoder passwordEncoder;
	 //forgot password controller logic
	 
	 @GetMapping("/forgot-password")
	    public String forgotPasswordPage()
	    {
	        return "forgot-password";
	    }
	  //email will be used to fetch and validates the user   
	    @PostMapping("/forgot-password")
	    public String processForgotPassword(@RequestParam String email)
	    {
	    	

	    	User user=userRepository.findByEmail(email);
	              //  .orElseThrow(() -> new RuntimeException("User not found"));
	    	    	String token=UUID.randomUUID().toString();
	    	    	//checking if the token already there in db forthis user
	    	    	Optional<PasswordResetToken> existingToken=
	    	    	        tokenRepository.findByUser(user);

	    	    	if(existingToken.isPresent())
	    	    	{
	    	    	    tokenRepository.delete(existingToken.get());
	    	    	}
	 PasswordResetToken resetToken=
	    	            new PasswordResetToken();
	    	    resetToken.setToken(token);
	    	    resetToken.setUser(user);
	    	    resetToken.setExpiryTime(
	    	            LocalDateTime.now().plusMinutes(30));
	    	    tokenRepository.save(resetToken);

	    	    String resetLink="http://localhost:8080/reset-password?token=" +token;
	    	         
	    	    SimpleMailMessage message=
	    	            new SimpleMailMessage();
	             //user email
	    	    message.setTo(user.getEmail());
	              //setting subject as password reset
	    	    message.setSubject("Password Reset");
	                         //generated link
	    	    message.setText(
	    	            "Click below link:\n"+resetLink);
	             //sending the mail
	    	    mailSender.send(message);
	    	    return "redirect:/login";
	    	}
	    //resetting password
	    @GetMapping("/reset-password")
	    public String resetPasswordPage(
	            @RequestParam String token,
	            Model model)
	    {

	        PasswordResetToken resetToken=
	                tokenRepository.findByToken(token)
	                .orElseThrow(() ->
	                        new RuntimeException("Invalid token"));


	        if(resetToken.getExpiryTime()
	                .isBefore(LocalDateTime.now()))
	        {
	            throw new RuntimeException("Token expired");
	        }


	        model.addAttribute("token", token);

	        return "reset-password";
	    }
	    //password reset 
	    @PostMapping("/reset-password")
	    public String resetPassword(
	            @RequestParam String token,
	            @RequestParam String password,
	            @RequestParam String confirmPassword,
	            Model model)
	    {

	    	if(!password.equals(confirmPassword))
	    	{
	    	    model.addAttribute(
	    	            "error",
	    	            "Passwords are not match");

	    	    model.addAttribute("token", token);

	    	    return "reset-password";
	    	}

	        PasswordResetToken resetToken=
	                tokenRepository.findByToken(token)
	                .orElseThrow(() ->
	                        new RuntimeException("Invalid token"));


	        User user=resetToken.getUser();


	        String encodedPassword=
	                passwordEncoder.encode(password);


	        user.setPassword(encodedPassword);

	        userRepository.save(user);


	        tokenRepository.delete(resetToken);


	        return "redirect:/login";
	    }
	    
	    
	    
	 
}
