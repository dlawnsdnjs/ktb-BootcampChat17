import { redirect } from 'next/navigation';

export default async function LoginRedirectPage({ searchParams }) {
  const params = await searchParams;
  const query = new URLSearchParams();

  Object.entries(params || {}).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((item) => query.append(key, item));
    } else if (value !== undefined) {
      query.set(key, value);
    }
  });

  redirect(query.size > 0 ? `/?${query.toString()}` : '/');
}
