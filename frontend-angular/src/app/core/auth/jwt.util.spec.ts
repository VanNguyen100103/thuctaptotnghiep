import { JwtPayload } from './auth.models';
import { decodeJwtPayload, isTokenExpired } from './jwt.util';

function base64url(input: string): string {
  return btoa(input).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function fakeToken(payload: object): string {
  const header = base64url(JSON.stringify({ alg: 'HS512', typ: 'JWT' }));
  const body = base64url(JSON.stringify(payload));
  return `${header}.${body}.fake-signature`;
}

describe('decodeJwtPayload', () => {
  it('decodes a well-formed payload, including base64url -/_ characters', () => {
    const payload: JwtPayload = {
      sub: 'owner1',
      userId: 42,
      email: 'owner@shop.vn',
      roles: 'ROLE_USER,ROLE_OWNER',
      storeId: 10,
      storeRole: 'OWNER',
      iat: 1000,
      exp: 2000,
    };
    // Force a string that base64-encodes to include '+' and '/' so the -/_ replacement path is exercised
    const padded = { ...payload, email: 'x'.repeat(40) + '@shop.vn' };

    const decoded = decodeJwtPayload<JwtPayload>(fakeToken(padded));

    expect(decoded).not.toBeNull();
    expect(decoded?.sub).toBe('owner1');
    expect(decoded?.userId).toBe(42);
    expect(decoded?.storeId).toBe(10);
    expect(decoded?.storeRole).toBe('OWNER');
  });

  it('returns null for a token with the wrong number of segments', () => {
    expect(decodeJwtPayload('not-a-jwt')).toBeNull();
    expect(decodeJwtPayload('a.b')).toBeNull();
    expect(decodeJwtPayload('a.b.c.d')).toBeNull();
  });

  it('returns null for invalid base64 in the payload segment', () => {
    expect(decodeJwtPayload('header.***not-base64***.sig')).toBeNull();
  });

  it('returns null for a payload segment that is not valid JSON', () => {
    const notJson = base64url('this is not json');
    expect(decodeJwtPayload(`header.${notJson}.sig`)).toBeNull();
  });
});

describe('isTokenExpired', () => {
  it('is false for a payload with exp in the future', () => {
    const payload = { exp: Math.floor(Date.now() / 1000) + 3600 } as JwtPayload;
    expect(isTokenExpired(payload)).toBe(false);
  });

  it('is true for a payload with exp in the past', () => {
    const payload = { exp: Math.floor(Date.now() / 1000) - 3600 } as JwtPayload;
    expect(isTokenExpired(payload)).toBe(true);
  });

  it('is true for null payload', () => {
    expect(isTokenExpired(null)).toBe(true);
  });

  it('is true when exp is missing', () => {
    expect(isTokenExpired({} as JwtPayload)).toBe(true);
  });
});
