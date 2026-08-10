export const NAME_MIN_LENGTH = 2;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 16;

export const EMAIL_PATTERN = '[^\\s@]+@[^\\s@]+\\.[^\\s@]+';

export const PASSWORD_PATTERN =
  `(?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[\\W_])` +
  `.{${PASSWORD_MIN_LENGTH},${PASSWORD_MAX_LENGTH}}`;

export const PASSWORD_HINT =
  `${PASSWORD_MIN_LENGTH}~${PASSWORD_MAX_LENGTH}자, 영문 대문자·소문자, 숫자, 특수문자 포함`;

export const REGISTER_MESSAGES = {
  nameRequired: '이름을 입력해주세요.',
  nameTooShort: `이름은 ${NAME_MIN_LENGTH}자 이상 입력해주세요.`,
  emailRequired: '이메일을 입력해주세요.',
  emailInvalid: '올바른 이메일 형식이 아닙니다.',
  passwordRequired: '비밀번호를 입력해주세요.',
  passwordRule: `비밀번호는 ${PASSWORD_HINT} 조건을 모두 만족해야 합니다.`,
  passwordConfirmRequired: '비밀번호 확인을 입력해주세요.',
  passwordMismatch: '비밀번호가 일치하지 않습니다.',
};
