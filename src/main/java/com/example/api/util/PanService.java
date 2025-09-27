package com.example.api.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PanService {

    @Value("${cards.validation.require-luhn:false}")
    private boolean requireLuhn;

    public String normalize(String raw){
        String d = raw.replaceAll("\\D", "");
        if (d.length() < 12) throw new IllegalArgumentException("invalid_pan_length");
        // Se vier "PAN+CVV" no layout, mantemos só os 16 primeiros dígitos
        if (d.length() > 16) d = d.substring(0, 16);
        if (requireLuhn && !luhn(d)) throw new IllegalArgumentException("invalid_pan_luhn");
        return d;
    }

    private static boolean luhn(String s){
        int sum=0, alt=0;
        for (int i=s.length()-1;i>=0;i--,alt^=1){
            int n=s.charAt(i)-'0';
            if (alt==1){ n<<=1; if(n>9)n-=9; }
            sum+=n;
        }
        return sum%10==0;
    }
    public String bin(String pan){ int n=Math.max(6, Math.min(8, pan.length()-4)); return pan.substring(0,n); }
    public String last4(String pan){ return pan.substring(pan.length()-4); }
}