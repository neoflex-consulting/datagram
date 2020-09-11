delete from etl_join_keyfields jkf where jkf.join_keyfields_e_id in (select st.e_id from etl_transformationstep st where not exists(select * from etl_transformation t where t.e_id = st.transformationstep_transformation_e_id));
delete from etl_join_joineekeyfields jkf where jkf.join_joineekeyfields_e_id in (select st.e_id from etl_transformationstep st where not exists(select * from etl_transformation t where t.e_id = st.transformationstep_transformation_e_id));
delete from etl_aggregation_groupbyfieldname where aggregation_groupbyfieldname_e_id in (select st.e_id from etl_transformationstep st where not exists(select * from etl_transformation t where t.e_id = st.transformationstep_transformation_e_id));
delete from etl_sortfeature where sort_sortfeatures_e_id in (select st.e_id from etl_transformationstep st where not exists(select * from etl_transformation t where t.e_id = st.transformationstep_transformation_e_id));
delete from etl_transformationstep st where not exists(select * from etl_transformation t where t.e_id = st.transformationstep_transformation_e_id);
delete from etl_source s where not exists(select * from etl_transformation t where t.e_id = s.source_transformation_e_id);
delete from etl_inputfieldmapping where inputfieldmapping_target_e_id in (select e_id from etl_target s where not exists(select * from etl_transformation t where t.e_id = s.target_transformation_e_id));
delete from etl_target s where not exists(select * from etl_transformation t where t.e_id = s.target_transformation_e_id);
delete from etl_projectionfield_sourcefields pfsf where field_e_id in(select e_id from dataset_field f where f.field_dataset_e_id in (select e_id from dataset_dataset d where not exists(select * from etl_transformationstep st where st.e_id::text = d.e_container)));
delete from dataset_field f where f.field_dataset_e_id in (select e_id from dataset_dataset d where not exists(select * from etl_transformationstep st where st.e_id::text = d.e_container));
delete from etl_debugoutput where outputport_debuglist_e_id is null;
delete from etl_debugoutput where outputport_debuglist_e_id in (select e_id from dataset_dataset d where d.e_container not in (select e_id::text from etl_source union select e_id::text from etl_target union select e_id::text from etl_transformationstep));
delete from etl_transition tr where not exists(select st.* from etl_transformation st where st.e_id = tr.transition_transformation_e_id);
delete from dataset_dataset d where d.e_container not in (select e_id::text from etl_source union select e_id::text from etl_target union select e_id::text from etl_transformationstep);
update etl_wfnode n set wfnode_error_e_id = null where wfnode_error_e_id in (select e_id from etl_wfnode n where not exists(select w.* from etl_workflow w where w.e_id = n.wfnode_workflow_e_id));
update etl_wfnode n set wfnode_default_e_id = null where wfnode_default_e_id in (select e_id from etl_wfnode n where not exists(select w.* from etl_workflow w where w.e_id = n.wfnode_workflow_e_id));
update etl_wfnode n set wfnode_to_e_id = null where wfnode_to_e_id in (select e_id from etl_wfnode n where not exists(select w.* from etl_workflow w where w.e_id = n.wfnode_workflow_e_id));
update etl_wfnode n set wfnode_ok_e_id = null where wfnode_ok_e_id in (select e_id from etl_wfnode n where not exists(select w.* from etl_workflow w where w.e_id = n.wfnode_workflow_e_id));
delete from etl_wfcase where wfnode_to_e_id in (select e_id from etl_wfnode n where not exists(select w.* from etl_workflow w where w.e_id = n.wfnode_workflow_e_id));
delete from etl_wfnode n where not exists(select w.* from etl_workflow w where w.e_id = n.wfnode_workflow_e_id);

delete from etl_property
where econtainer_class='rt.WorkflowDeployment'
      and cast(e_container as BIGINT) not in (select e_id from rt_workflowdeployment);
