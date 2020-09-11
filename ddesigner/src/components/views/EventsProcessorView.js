import React, { Component, Fragment } from 'react';
import {Layout, Row, Col, Table, Input, Modal, Tabs, Button, Avatar, Menu, Dropdown, Form, Select, Checkbox, Tooltip} from 'antd';
import update from 'immutability-helper';
import SplitterLayout from 'react-splitter-layout';
import resource from "./../../Resource";
import { getEntityClassFeature, getModel } from '../../model.js';
import { translate } from "react-i18next";
import EmbeddedForm from './EmbeddedForm'
import AceEditor from 'react-ace';

import { RenderCondition, RenderAction, RenderActionPanel, RenderRulesTree, RenderRuleAction, MoveRulesElement,
    EventsProcessorDataStructure, DoActionWithDataSetsField, GetTreeFieldByPath, DeleteRulesElement,
    WrapSearchableComponent, IsSearchStringContains} from './EventsProcessorComponents';
import {instantiate} from "../../utils/meta";
import Debounced from "../Debounced";
import _ from "lodash";

const { Content } = Layout;
const Search = Input.Search;
const { TextArea } = Input;
const FormItem = Form.Item;
const Option = Select.Option;

class EventsProcessorView extends Component {

    constructor(...args) {
        super(...args);
        this.model = getModel();
        this.state = {
            functions: [],
            projects: [],
            expandedRules: [],
            filterState: {
                filterStr: undefined,
                editableIds: [],
                filterExpandedRulesOverride: []
            }
        };
        this.editDialog = undefined
        this.searchDebounced = undefined;
    }

    treeOnClick() {

    }

    RuleKeywordsFound(text) {
        this.props.entity.rules.forEach((r, i) => {
            if (r.name === text) {
                if (this.state.expandedRules.indexOf(i) < 0 &&
                    this.state.filterState.filterExpandedRulesOverride.indexOf(i) < 0) {

                    let newList = this.state.expandedRules;
                    newList.push(i);

                    this.setState(update(this.state, {
                        expandedRules: {
                            $set: newList
                        }
                    }));
                }
            }
        });
    }

    RulesRowRenderer(updateEntity, rules) {

        return (text) => {

            this.props.entity.rules.forEach((r) => {
                if(r.name === text) {
                    if (IsSearchStringContains(text, this.state.filterState)) {
                        this.RuleKeywordsFound(text);
                    }
                }
            });

            return (<div className={"ruleTableRow"}>
                <div className={"ruleRowTitle"}>
                    <WrapSearchableComponent
                        filterState={this.state.filterState}
                        elementId={text}
                        value={text}
                    >
                        <span>{text}</span>
                    </WrapSearchableComponent>
                </div>
                <RenderRuleAction
                    text={text}
                    upAction={(text) => {
                        MoveRulesElement(updateEntity, rules, text, true);
                    }}
                    downAction={(text) => {
                        MoveRulesElement(updateEntity, rules, text, false);
                    }}
                    deleteAction={(text) => {
                        DeleteRulesElement(updateEntity, rules, text);
                    }}
                />
            </div>);
        };
    }

    Search(value) {
        let filterState = {
            filterStr: value.toUpperCase(),
            editableIds: [],
            filterExpandedRulesOverride: [],
            edit: ((elementId) => {
                let newfilterState = update(this.state.filterState,
                    {editableIds: {$push: [elementId]}})
                this.setState({filterState: newfilterState});
            })
        };

        this.setState({filterState});
    }

    render() {
        const {t} = this.props;

        const createAddRuleMenu = <Menu onClick={e => {
            e.domEvent.stopPropagation();

            let newRule = instantiate(e.key);

            let oldRules = this.props.entity.rules || [];

            let newIndex = 1;

            for(;;) {

                let isNameExists = false;

                for (let i = 0; i < oldRules.length; i++) {
                    if (oldRules[i].name === "" + newIndex) {
                        isNameExists = true;
                        break;
                    }
                }

                if (isNameExists) {
                    newIndex++;
                } else {
                    break;
                }
            }

            newRule.name = "" + newIndex;

            let newRules = update(oldRules, {$push: [newRule]});

            this.props.updateEntity({
                    rules: newRules
                });

        }}>
            {getEntityClassFeature(this.model, "evs.Rule", {
                __parent: this.props.entity
            }, "successors").map(embeddedType =>
                <Menu.Item
                    key={embeddedType}>{t(`${embeddedType}.caption`, {ns: 'classes'})}</Menu.Item>)}
        </Menu>; //`

        return(
              <Layout>
                <Content className={"designer-content"}>
                    <SplitterLayout vertical={false} primaryIndex={0} secondaryInitialSize={300} customClassName={"splitter-layout-custom"}>
                        <div>
                            <div className={"rulesToolbar"}>
                                <div className={"searchField"}>
                                    <Search style={{ marginBottom: 8 }}
                                            onChange={e => {
                                                if (this.searchDebounced) {
                                                    this.searchDebounced.cancel();
                                                }

                                                this.searchDebounced = _.debounce(this.Search.bind(this),500);
                                                this.searchDebounced(e.target.value);
                                            }}
                                    />
                                </div>
                                <div className={"newRuleButton"}>
                                    <Dropdown overlay={createAddRuleMenu}>
                                        <Button onClick={e => {
                                            e.stopPropagation()
                                        }}>
                                            <Avatar className='avatar-add' src='images/icon-core/file-add.svg'/>
                                        </Button>
                                    </Dropdown>
                                </div>
                            </div>

                            {this.createPropertyEditor()}

                            <Table
                                className={"rulesTable"}
                                dataSource={this.props.entity.rules}
                                columns={[{
                                    Title:      'Rule',
                                    dataIndex:  'name',
                                    key:        'name',
                                    render:     this.RulesRowRenderer(this.props.updateEntity, this.props.entity.rules)
                                }]}
                                expandedRowRender={(row) => this.renderRule(row)}
                                pagination={false}
                                showHeader={false}
                                rowKey={(record)=>this.props.entity.rules.indexOf(record)}
                                expandedRowKeys={this.state.expandedRules}
                                onExpand={(expanded, record)=>{

                                    const idx = this.props.entity.rules.indexOf(record);

                                    let newRules = this.state.expandedRules;
                                    let newFilterRules = this.state.filterState.filterExpandedRulesOverride;

                                    if(expanded) {
                                        if (newRules.indexOf(idx) < 0) {
                                            newRules.push(idx);
                                        }
                                    } else {
                                        if (newRules.indexOf(idx) >= 0) {
                                            newRules.splice(newRules.indexOf(idx), 1);
                                        }

                                        if (newFilterRules.indexOf(idx) < 0) {
                                            newFilterRules.push(idx);
                                        }
                                    }

                                    this.setState(update(this.state, {
                                        expandedRules: {
                                            $set: newRules
                                        },
                                        filterState: {
                                            filterExpandedRulesOverride: {
                                                $set: newFilterRules
                                            }
                                        }}));

                                    if(expanded) {
                                        if(record._type_ === "evs.Rule") {
                                            if(!record.condition) {
                                                const ruleUpdated = update(record, {$merge: {condition: {_type_: "evs.AndCondition", conditions: []}}});
                                                this.updateRule(record, ruleUpdated)
                                            }
                                        }
                                    }
                                }}
                                />
                        </div>
                        <div className={"rulesTree"}>
                            <Tabs onChange={this.tabsChanged} type="card">
                                <Tabs.TabPane tab="Data" key="1">

                                    <RenderRulesTree entity={this.props.entity}
                                                     data={this.eventsProcessorDataStructure}
                                                     updateEntity={this.props.updateEntity}
                                                     openEditDialog={(path) => {
                                                         this.openEditDialog(path)
                                                     }}
                                                     translate={this.props.t}
                                    />
                                    {this.createEditDialog()}

                                </Tabs.TabPane>
                                <Tabs.TabPane tab="DRL" key="drl">
                                <Fragment>
                                    <Form layout={"inline"}>
                                        <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                                            <Tooltip placement="top" title={t("Apply")}>
                                                <Button id="apply" shape="circle" style={{ border: 0 }}
                                                    onClick={() => {
                                                        const updatedEntity = update(this.props.entity, {$merge: {body: this.state.drl}});
                                                        this.props.updateEntity(updatedEntity)
                                                    }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/check-modern.svg"} />
                                                </Button>
                                            </Tooltip>
                                        </Form.Item>
                                    {this.state.drlLoad &&
                                        <div style={{alignItems: 'center'}}>
                                        </div>
                                    }
                                    {!this.state.drlLoad &&
                                        <AceEditor
                                            mode={'scala'}
                                            width={''}
                                            height={600}
                                            theme={'tomorrow'}
                                            fontSize={15}
                                            editorProps={{ $blockScrolling: Infinity }}
                                            value={this.state.drl}
                                            onChange={newValue => this.setState({drl: newValue})}
                                            showPrintMargin={false}
                                        />
                                    }
                                        </Form>
                                    </Fragment>
                                </Tabs.TabPane>
                            </Tabs>
                        </div>
                    </SplitterLayout>
                </Content>
              </Layout>)
     }

     findObjectInList(list, value) {

         let object = undefined;

         let eId = value ? (value.e_id ? value.e_id : value) : undefined;

         if (!eId) {
             return object;
         }

         list.forEach((item)=>{
             if(item.e_id && item.e_id === eId) {
                 object = item;
             }
         });

         return object;
     }

    createPropertyEditor() {

        let selectedProject = this.findObjectInList(this.state.projects, this.props.entity.project);

        return <div className="processorPropertyEditor"><Row>
            <Col span={12}>
                <FormItem key="name" label={this.props.t("evs.EventsProcessor.attrs.name.caption", { ns: ['classes'] })}
                          labelCol={{span: 4}}
                          wrapperCol={{span: 20}}>
                    <WrapSearchableComponent
                        filterState={this.state.filterState}
                        filterApplied={() => {}}
                        elementId="name"
                        value={this.props.entity.name}
                    >
                        <Debounced id="name"
                                   value={this.props.entity.name} onChange={e =>
                            this.props.updateEntity({name: e.target.value})
                        }/>
                    </WrapSearchableComponent>
                </FormItem>
            </Col>
            <Col span={12}>
                <FormItem key="project" label={this.props.t("evs.EventsProcessor.attrs.project.caption", { ns: ['classes'] })}
                          labelCol={{span: 4}}
                          wrapperCol={{span: 20}}>
                    <WrapSearchableComponent
                        filterState={this.state.filterState}
                        filterApplied={() => {}}
                        elementId="project"
                        value={selectedProject ? selectedProject.name : undefined}
                    >
                        <Select allowClear id="project" value={selectedProject ? selectedProject.e_id : undefined}
                                style={{width: "100%"}}
                                onChange={value => {
                                    this.props.updateEntity({project: this.findObjectInList(this.state.projects, value)})
                                }}>
                            {this.state.projects.map((ent, index) =>
                                <Option key={ent.e_id} value={ent.e_id}>{_.get(ent, 'name', ent.name)}</Option>
                            )}
                        </Select>
                    </WrapSearchableComponent>
                </FormItem>
            </Col>
        </Row>
        <Row>
            <Col span={24}>
                <FormItem key="description" label={this.props.t("evs.EventsProcessor.attrs.description.caption", { ns: ['classes'] })}
                          labelCol={{span: 2}}
                          wrapperCol={{span: 22}}>
                    <WrapSearchableComponent
                        filterState={this.state.filterState}
                        filterApplied={() => {}}
                        elementId="description"
                        value={this.props.entity.description}
                    >
                        <Debounced Component={TextArea} autosize={{minRows: 2, maxRows: 2}} id="description"
                                   value={this.props.entity.description} onChange={e =>
                            this.props.updateEntity({description: e.target.value})
                        }/>
                    </WrapSearchableComponent>
                </FormItem>
            </Col>
        </Row>
        </div>
    }

    openEditDialog(path) {

        const field = GetTreeFieldByPath(this.eventsProcessorDataStructure, path);

        this.setState(update(this.state, {editDialog: {$set: {
                    entity: field,
                    path: path
                }}}));
    }

    updateEditedEntity(args) {

        let editDialog = this.state.editDialog;

        editDialog.entity = update(editDialog.entity, {$merge: args});

        this.setState(update(this.state, {
            editDialog: {$set: editDialog}
        }));

    }

    saveEditedEntity() {

        const fieldPath = this.state.editDialog.path;
        const field = this.state.editDialog.entity;

        DoActionWithDataSetsField(this.eventsProcessorDataStructure, fieldPath, this.props.updateEntity,
            (parent, current, path) => {
                return update(current, {$merge: field})
        });
    }

    createEditDialog() {

        if (!this.state.editDialog) {
            return null;
        }

        const {t} = this.props;

        const entity = this.state.editDialog.entity;
        const title = t("editing") + " " + entity.name + " " + (entity.dataTypeDomain ? entity.dataTypeDomain : "");
        const actionName = undefined;

        let context = {};

        return (
            <div>
            <Modal title={title}
                visible={true}
                cancelText={t('cancel')}
                okText={t('save')}
                onCancel={() => {

                    this.setState(update(this.state, {editDialog: {$set: undefined}}));
                }}
                onOk={(e) => {
                    this.setState(update(this.state, {editDialog: {$set: undefined}}));
                    this.saveEditedEntity();
                }}
            >
                <EmbeddedForm
                    updateEntity={args => {
                        this.updateEditedEntity(args);
                    }}
                    context={context}
                    entity={this.state.editDialog.entity}
                    actionName={actionName}
                    updateContext={(val, cb) => {
                    }}
                />
            </Modal>
            </div>
        )
    }

    tabsChanged = (key) => {
        if(key === "drl") {
           this.generateRules()
        }
    };

     generateRules() {
         resource.call(this.props.entity, 'generateDrl', {}).then(json => {
             if(json.result === true) {
                 this.setState({drl: json.fileContent,drlLoad:  false});
             }
         }).catch(() => this.setState({drl: null, drlLoad: false})
        )
    }

     updateRule(oldRule, newRule) {
         let ruleIndex = this.props.entity.rules.indexOf(oldRule)
         let newEntity = update(this.props.entity, {
                 rules: {
                     [ruleIndex]: {$merge: newRule}
                 }
             }
         );
         this.props.updateEntity(newEntity)
     }

     renderRule(rule) {

         const idx = this.props.entity.rules.indexOf(rule);

         const {t} = this.props;
         const data = [{"name": t('When', { ns: 'common' }), "rule": rule, "key": "when_row"}, {"name": t('Then', { ns: 'common' }), "rule": rule, "key": "then_row"}]
         return (
             <Row>
                 <Col span={1}/>
                 <Col span={23}>
                     <div className={"collapsibleRow"}>

                         <Row>
                             <Col span={2}>
                                 <div className={"rulesInputRowTitle"}>
                                    <b>{this.props.t("evs.EventsProcessor.attrs.name.caption", { ns: ['classes'] })}</b>
                                 </div>
                             </Col>
                             <Col span={22}>
                                 <Debounced id="name"
                                    value={rule.name} onChange={e => {
                                         const updatedRule = update(rule, {$merge: {name: e.target.value}});
                                         const updatedEntity = update(this.props.entity, {rules: {[idx]: {$merge: updatedRule}}}
                                         );
                                    this.props.updateEntity(updatedEntity)
                                    }
                                 }/>
                             </Col>
                         </Row>
                         <Row>
                             <Col span={2}>
                             </Col>
                             <Col span={22}>
                                 <Checkbox id="modifyFact"
                                    style={{margin: "4px"}}
                                    checked={rule.modifyFact}
                                    onChange={e => {
                                         const updatedRule = update(rule, {$merge: {modifyFact: e.target.checked}});
                                         const updatedEntity = update(this.props.entity, {rules: {[idx]: {$merge: updatedRule}}}
                                         );
                                    this.props.updateEntity(updatedEntity)
                                    }}>
                                 {this.props.t("evs.Rule.attrs.name.modifyFact", { ns: ['classes'] })}
                                 </Checkbox>
                             </Col>
                         </Row>
                         <div className={"rulesInputRowTitleSplitter"}> </div>

                        {data.map(row=>this.renderRuleWhenThen(row))}
                     </div>
                 </Col>
             </Row>
         )
     }

     getRuleIndex(condition) {
         var result = -1;
         this.props.entity.rules.forEach((r,i) => {
             if((r.conditions || []).indexOf(condition) !== -1) {
                 result = i
             }
         });
         return result
     }

     renderWhenRow(row) {
         const {t} = this.props;
         const updateEntity = (changes)=>{
             const idx = this.props.entity.rules.indexOf(row.rule);
             const updatedRule = update(row.rule, {$merge: {condition: changes}});
             const changedEntity = update(this.props.entity, {
                     rules: {
                         [idx]: {$merge: updatedRule}
                     }
                 }
             );
             this.props.updateEntity(changedEntity)
         };
         return <RenderCondition
                    elementId={row.rule.name}
                    condition={row.rule.condition}
                    index={0}
                    rule={row.rule}
                    parentCondition={null}
                    updateEntity={updateEntity}
                    deleteItem={null}
                    data={this.eventsProcessorDataStructure}
                    filterState={this.state.filterState}
                    filterApplied={() => {
                        this.RuleKeywordsFound(row.name);
                    }}
                    t={t}/>
     }

     renderThenRow(row) {
         const {t} = this.props;
         const updateEntity = (oldAction, updatedAction)=>{
             const idx = this.props.entity.rules.indexOf(row.rule);
             const actionIdx = (row.rule.actions || []).indexOf(oldAction);
             let updatedRule;
             if(actionIdx !== -1) {
                 updatedRule = update(row.rule, {actions: {[actionIdx]: {$merge: updatedAction}}})
             } else {
                 const newActions = update(row.rule.actions || [], {$push: [updatedAction]})
                 updatedRule = update(row.rule, {actions: {$merge: newActions}})
             }

             const changedEntity = update(this.props.entity, {
                     rules: {
                         [idx]: {$merge: updatedRule}
                     }
                 }
             );

             this.props.updateEntity(changedEntity)
         };
         return <div>
            <Row>
                <Col span={21}>{t("fireActions", { ns: 'common' })}</Col>
                <Col span={3}>
                    <RenderActionPanel rule={row.rule} OnNewAction={(rule, action) => {updateEntity(undefined, action)}} t={t}/>
                </Col>
            </Row>
                {(row.rule.actions || []).map((action, index)=><RenderAction
                    elementId={row.rule.name + "_" + row.name + "_action_" + index}
                    key={"action_" +index}
                    action={action}
                    functions={this.state.functions}
                    data={this.eventsProcessorDataStructure}
                    updateEntity={updateEntity}
                    filterState={this.state.filterState}
                    filterApplied={() => {
                        this.RuleKeywordsFound(row.name);
                    }}
                    deleteAction={()=>{
                        Modal.confirm({
                            content: t("confirmdelete"),
                            okText: t("delete"),
                            cancelText: t("cancel"),
                            onOk: () => {
                                const updated = update(row.rule, {
                                    $merge:{
                                        actions: update(row.rule.actions, {$splice: [[index, 1]]})
                                    }
                                });
                                this.updateRule(row.rule, updated);
                            }
                        });
                    }}
                    upAction={() => {
                        if (index > 0) {
                            let list = row.rule.actions.slice();
                            const updated = update(row.rule, {$merge:
                                {actions: [
                                    ...list.slice(0, index - 1),
                                    list[index],
                                    list[index - 1],
                                    ...list.slice(index + 1)
                                ]}
                            });
                            this.updateRule(row.rule, updated);
                        }
                    }}
                    downAction={() => {
                        let list = row.rule.actions.slice();
                        if (index >= 0 && index < list.length - 1) {
                            const updated = update(row.rule, {$merge:
                                {actions: [
                                    ...list.slice(0, index),
                                    list[index + 1],
                                    list[index],
                                    ...list.slice(index + 2)
                                ]}
                            });
                            this.updateRule(row.rule, updated);
                        }
                    }}
                    t={t}/>)}
                </div>
     }

     renderRuleWhenThen(row) {
         return (
             <Row key={row.name}>
                 <Col span={2}><b>{row.name}</b></Col>
                 <Col span={22}>
                    {row.key === "when_row" &&
                        <div>{this.renderWhenRow(row)}</div>
                    }
                    {row.key === "then_row" &&
                        <div>{this.renderThenRow(row)}</div>
                    }
                 </Col>
             </Row>
         )
     }

    getDisplayFieldName(field) {
        var dsname = this.eventsProcessorDataStructure.getDataSetAlias(field.dataSet);
        return dsname + "." + field.name
    }

    componentDidMount() {
        resource.query(`/api/teneo/select/from evs.EventFunction?__up=0&__down=0&__deep=2`)
         .then(data => {
             this.setState(update(this.state, {functions: {$set: data}}))
         });

        resource.getSimpleSelect("etl.Project", ["name"]).then(list => {
            this.setState(update(this.state, {projects: {$set: list}}));
        });
    }

    componentDidUpdate(){
        this.eventsProcessorDataStructure = new EventsProcessorDataStructure(this.props.entity.input, this.props.entity.state, this.props.entity.local, this.props.entity.output)
    }
}

export default translate()(EventsProcessorView);
