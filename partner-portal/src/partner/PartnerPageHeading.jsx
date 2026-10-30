export function PartnerPageHeading({ title, description, children }) {
  return <header className="portal-page-heading">
    <div className="portal-page-heading-copy"><h1>{title}</h1>{description&&<p>{description}</p>}</div>
    {children&&<div className="portal-page-actions">{children}</div>}
  </header>;
}
