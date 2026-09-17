package com.flashticket.authservice.mapper;

import com.flashticket.authservice.entity.AuthAccount;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthAccountMapper {

    AuthAccount findByEmail(String email);

    int insert(AuthAccount authAccount);
}
