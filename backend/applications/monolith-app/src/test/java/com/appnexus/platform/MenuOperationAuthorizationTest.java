package com.appnexus.platform;

import com.appnexus.platform.authorization.AccessAuthorization;
import com.appnexus.platform.persistence.platformscope.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 平台管理员身份不代表菜单全部操作；缺少对应操作时必须在任何目录写入前拒绝。 */
class MenuOperationAuthorizationTest {
    @Test void administratorWithoutIndividualOperationCannotWrite(){
        var mapper=mock(MenuMapper.class);var authorization=mock(AccessAuthorization.class);UUID actor=UUID.randomUUID();
        when(mapper.lockAdministrator(actor.toString())).thenReturn(actor.toString());
        var service=new MyBatisMenuManagement(mapper,authorization);
        assertThatThrownBy(()->service.create(actor,null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.update(actor,UUID.randomUUID(),0,null)).isInstanceOf(SecurityException.class);
        assertThatThrownBy(()->service.delete(actor,UUID.randomUUID(),0)).isInstanceOf(SecurityException.class);
        var subject=AccessAuthorization.Subject.platform(actor);
        verify(authorization).hasPermission(subject,"configuration.menu:create");
        verify(authorization).hasPermission(subject,"configuration.menu:update");
        verify(authorization).hasPermission(subject,"configuration.menu:delete");
        verify(mapper,never()).lockCatalog();
    }
}
