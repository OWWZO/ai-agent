package org.wwz.ai.test.infrastructure;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.wwz.ai.domain.auth.entity.UserAccount;
import org.wwz.ai.domain.auth.exception.LoginNameAlreadyExistsException;
import org.wwz.ai.infrastructure.adapter.repository.UserAccountRepository;
import org.wwz.ai.infrastructure.dao.IUserAccountDao;
import org.wwz.ai.infrastructure.dao.po.UserAccountPO;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class UserAccountRepositoryTest {

    @Test
    public void shouldTranslatePersistenceConstraintFailureAtRepositoryBoundary() {
        IUserAccountDao dao = mock(IUserAccountDao.class);
        doThrow(new DataIntegrityViolationException("duplicate login name"))
                .when(dao).insert(any(UserAccountPO.class));
        UserAccountRepository repository = new UserAccountRepository(dao);

        Assert.assertThrows(LoginNameAlreadyExistsException.class,
                () -> repository.save(UserAccount.builder().loginName("member").build()));
    }
}
