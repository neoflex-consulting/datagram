import React, {Component} from 'react';
import {translate} from 'react-i18next';
import {Select, Col, Button, Input, Row, Form, Table, Checkbox} from 'antd';
import resource from '../../Resource';
//import moment from 'moment'
import _ from 'lodash'

const {Column} = Table;
const {Option} = Select;

const COMMANDS = Object.freeze({
    REFRESH_INFO: Symbol("REFRESH_INFO"),
    SET_CURRENT: Symbol("SET_CURRENT"),
    EXPORT: Symbol("EXPORT"),
    IMPORT: Symbol("IMPORT"),
    IMPORTREFS: Symbol("IMPORTREFS"),
    CLONE: Symbol("CLONE"),
    DELETE: Symbol("DELETE"),
    PUSH: Symbol("PUSH"),
    PULL: Symbol("PULL"),
    RESET: Symbol("RESET"),
    MERGE: Symbol("MERGE"),
    CHANGES: Symbol("CHANGES"),
})

class BranchCommandForm extends React.Component {
    state = {count: 0}
    handleSubmit = e => {
        e.preventDefault();
        const {context} = this.props;
        const actions = {
            [COMMANDS.REFRESH_INFO]: (values) => {
                resource.query(`/system/branch`, {
                    method: "GET",
                    headers: {
                        'Content-Type': 'application/json'
                    }
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
            [COMMANDS.SET_CURRENT]: (values) => {
                resource.query(`/system/branch/${values.branch}`, {
                    method: "PUT",
                    headers: {
                        'Content-Type': 'application/json'
                    }
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
            [COMMANDS.CLONE]: (values) => {
                resource.query(`/system/branch`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({branch: values.branchName})
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
            [COMMANDS.CHANGES]: (values) => {
                resource.query(`/system/changes?from=${values.from?encodeURIComponent(values.from):''}&to=${values.to?encodeURIComponent(values.to):''}`, {
                    method: "GET",
                    headers: {
                        'Content-Type': 'application/json'
                    }
                }).then(list => {
                    console.log(list)
                });
            },
            [COMMANDS.EXPORT]: (values) => {
                resource.query(`/system/export`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({})
                }).then(list => {
                    console.log(list)
                });
            },
            [COMMANDS.IMPORT]: (values) => {
                resource.query(`/system/import`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({truncate: values.truncate||null})
                }).then(list => {
                    console.log(list)
                });
            },
            [COMMANDS.IMPORTREFS]: (values) => {
                resource.query(`/system/importrefs`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({})
                }).then(list => {
                    console.log(list)
                });
            },
            [COMMANDS.DELETE]: (values) => {
                resource.query(`/system/branch/${context.branchInfo.current}`, {
                    method: "DELETE",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({username: values.username||null,
                        password: values.password||null})
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
            [COMMANDS.PULL]: (values) => {
                resource.query(`/system/pull`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({remote: values.remote||null, username: values.username||null,
                        password: values.password||null, remoteBranch: values.remoteBranch||null, strategy: values.strategy||null})
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
            [COMMANDS.RESET]: (values) => {
                resource.query(`/system/reset`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({})
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
            [COMMANDS.PUSH]: (values) => {
                resource.query(`/system/push`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({remote: values.remote||null, username: values.username||null,
                        password: values.password||null})
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
            [COMMANDS.MERGE]: (values) => {
                resource.query(`/system/merge`, {
                    method: "POST",
                    headers: {
                        'Content-Type': 'application/json'
                    },
                    body: JSON.stringify({toBranch: values.toBranch})
                }).then(branchInfo => {
                    this.props.context.updateContext({branchInfo})
                    localStorage.setItem("currentBranch", branchInfo.current)
                });
            },
        }
        this.props.form.validateFields((err, values) => {
            if (!err) {
                actions[COMMANDS[values.command]](values)
            }
        });
    };

    render() {
        const {getFieldValue, getFieldDecorator} = this.props.form;
        const {context} = this.props;
        return (
            <Form onSubmit={this.handleSubmit} className="login-form">
                <Form.Item label="Command">
                    {getFieldDecorator('command', {
                        rules: [{required: true, message: 'Please select command'}],
                    })(
                        <Select
                            placeholder="Select a command to run"
                        >
                            {Object.keys(COMMANDS).map(command => <Option value={command}>{command}</Option>)}
                        </Select>
                    )}
                </Form.Item>
                {[COMMANDS.SET_CURRENT].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Branch">
                    {getFieldDecorator('branch', {
                        rules: [{required: true, message: 'Please select branch'}],
                    })(
                        <Select
                            placeholder="Select a branch"
                        >
                            {Object.keys(context.branchInfo.branches).map(branch => <Option value={branch}>{branch}</Option>)}
                        </Select>
                    )}
                </Form.Item>}
                {[COMMANDS.CLONE].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Branch Name">
                    {getFieldDecorator('branchName', {
                        rules: [{required: true, message: 'Please input branch name'}],
                    })(
                        <Input placeholder="Branch name"/>
                    )}
                </Form.Item>}
                {[COMMANDS.IMPORT].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Truncate current DB">
                    {getFieldDecorator('truncate', {
                        valuePropName: 'checked',
                        initialValue: false,
                    })(<Checkbox/>)}

                </Form.Item>}
                {[COMMANDS.MERGE].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="To Branch">
                    {getFieldDecorator('toBranch', {
                        rules: [{required: true, message: 'Please select merge to branch'}],
                    })(
                        <Select
                            placeholder="Select merge to branch"
                        >
                            {Object.keys(context.branchInfo.branches).map(branch => <Option value={branch}>{branch}</Option>)}
                        </Select>
                    )}
                </Form.Item>}
                {[COMMANDS.PULL, COMMANDS.PUSH].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Remote">
                    {getFieldDecorator('remote', {
                        rules: [{required: false, message: 'Please select remote'}],
                    })(
                        <Input placeholder="Remote"/>
                    )}
                </Form.Item>}
                {[COMMANDS.PULL].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Merge Strategy">
                    {getFieldDecorator('strategy', {
                        rules: [{required: false, message: 'Please select merge strategy'}],
                    })(
                    <Select
                        placeholder="Select merge strategy">
                        <Option value="default">DEFAULT</Option>
                        <Option value="ours">OURS</Option>
                        <Option value="theirs">THEIRS</Option>
                    </Select>
                    )}
                </Form.Item>}
                {[COMMANDS.PULL].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Remote Branch">
                    {getFieldDecorator('remoteBranch', {
                        rules: [{required: false, message: 'Please enter remote branch'}],
                    })(
                        <Input placeholder="Remote Branch"/>
                    )}
                </Form.Item>}
                {[COMMANDS.PULL, COMMANDS.PUSH, COMMANDS.DELETE].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="User Name">
                    {getFieldDecorator('username', {
                        rules: [{required: false, message: 'Please enter user name'}],
                    })(
                        <Input placeholder="User Name"/>
                    )}
                </Form.Item>}
                {[COMMANDS.PULL, COMMANDS.PUSH, COMMANDS.DELETE].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Password">
                    {getFieldDecorator('password', {
                        rules: [{required: false, message: 'Please enter password'}],
                    })(
                        <Input placeholder="Password" type="password"/>
                    )}
                </Form.Item>}
                {[COMMANDS.CHANGES].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Show changes From reference">
                    {getFieldDecorator('from', {
                        rules: [{required: false, message: 'Please enter From reference'}],
                    })(
                        <Input placeholder="From reference"/>
                    )}
                </Form.Item>}
                {[COMMANDS.CHANGES].includes(COMMANDS[getFieldValue("command")]) &&
                <Form.Item label="Show changes To reference">
                    {getFieldDecorator('to', {
                        rules: [{required: false, message: 'Please enter To reference'}],
                    })(
                        <Input placeholder="To reference"/>
                    )}
                </Form.Item>}
                <Button type="primary" htmlType="submit" className="login-form-button">Run</Button>
            </Form>
        )
    }
}

const WrappedBranchCommandForm = Form.create({name: 'branch_command'})(BranchCommandForm);

class BranchesInfo extends Component {

    render() {
        const {t, context} = this.props
        const branches = _.sortBy(Object.keys(context.branchInfo.branches), key => key.toLowerCase()).map(branch => ({
            key: branch,
            branch,
            // parent: context.branchInfo.branches[branch].parent !== branch ? context.branchInfo.branches[branch].parent : undefined,
            // created: context.branchInfo.branches[branch].created,
            isCurrent: context.branchInfo.current === branch
        }))
        return (
            <Row>
                <Col span={3}/>
                <Col span={18}>
                    <WrappedBranchCommandForm {...this.props} />
                    <Table pagination={false} size="small" dataSource={branches}>
                        <Column
                            title={t("ui3.ApplicationInfo.attrs.branchIsCurrent.caption", {ns: "classes"})}
                            dataIndex={"isCurrent"}
                            key={"isCurrent"}
                            render={(text) => <Checkbox disabled={true} checked={text === true}/>}
                        />
                        <Column title={t("ui3.ApplicationInfo.attrs.branchName.caption", {ns: "classes"})}
                                dataIndex={"branch"} key={"branch"}/>
                        {/*<Column title={t("ui3.ApplicationInfo.attrs.branchCreated.caption", {ns: "classes"})}*/}
                        {/*        dataIndex={"created"} key={"created"}/>*/}
                        {/*<Column title={t("ui3.ApplicationInfo.attrs.branchParent.caption", {ns: "classes"})}*/}
                        {/*        dataIndex={"parent"} key={"parent"}/>*/}
                    </Table>
                </Col>
                <Col span={3}/>
            </Row>
        )
    }

}

export default translate()(BranchesInfo);
