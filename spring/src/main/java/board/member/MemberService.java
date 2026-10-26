package board.member;

import board.crypto.FieldCrypto;
import board.crypto.FieldCrypto.Encrypted;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 5-2 저장 전에 암호화, 검색은 블라인드 인덱스로 */
@Service
public class MemberService {
    private final MemberRepository members;
    private final FieldCrypto crypto;

    public MemberService(MemberRepository members, FieldCrypto crypto) {
        this.members = members;
        this.crypto = crypto;
    }

    public Member create(String nickname, String email, String phone) {
        Member m = new Member(nickname, email);
        Encrypted e = crypto.encrypt(phone);
        // 저장: phone_ct(+iv · 감싼 키), phone_idx
        m.phone(e.ct(), e.iv(), e.wrappedKey(), crypto.blindIndex(phone));
        return members.save(m);
    }

    /** 조회: WHERE phone_idx = ? */
    public List<Member> findByPhone(String phone) {
        return members.findByPhoneIdxAndWithdrawnAtIsNullOrderByIdAsc(crypto.blindIndex(phone));
    }

    public Optional<Member> find(long id) {
        return members.findByIdAndWithdrawnAtIsNull(id);
    }

    public String phoneOf(Member m) throws GeneralSecurityException {
        if (m.getPhoneCt() == null) return null;
        return crypto.decrypt(new Encrypted(m.getPhoneCt(), m.getPhoneIv(), m.getPhoneKey()));
    }
}
