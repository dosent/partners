select application.id as application_id,
       latest.submitted_at,
       latest.revision_number
from partner_application application
join lateral (
    select revision.submitted_at, revision.revision_number
    from partner_application_revision revision
    where revision.application_id = application.id
    order by revision.revision_number desc
    limit 1
) latest on true
where application.status = 'MANUAL_REVIEW'
order by latest.submitted_at asc, application.id asc
offset :offset rows fetch next :limit rows only
