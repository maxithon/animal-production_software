delete from public.livestock_abortions;
delete from public.livestock_births;
delete from public.livestock_breeding;
delete from public.livestock_deaths;
delete from public.livestock_offspring;
delete from public.livestock_sales;
delete from public.livestock_sick;
delete from public.livestock_sick_history;
delete from public.livestock_treatments;
delete from public.livestock_valuation_history;
delete from public.livestock;
delete from public.livestock_categories;
delete from public.aps_log;
delete from public.audit_log;
delete from public.beneficiaries;
delete from public.buyers;
delete from public.medications;
delete from public.representatives;
delete from public.user_modules;
delete from public.user_type_modules;
delete from public.modules;
delete from public.veterinarians;




DROP VIEW public.v_females_ready_to_breed;
DROP VIEW public.v_livestock_with_age;
DROP VIEW public.v_males_ready_to_breed;
DROP VIEW public.v_newborn_animals;
DROP VIEW public.v_pregnant_animals;
DROP VIEW public.v_user_permissions;
DROP VIEW public.v_young_animals;