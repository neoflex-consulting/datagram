import React, {Component} from 'react';
import {translate} from 'react-i18next';
import {Tabs, Table} from 'antd';
import resource from '../../Resource';
import moment from 'moment'
import _ from 'lodash'
import BranchesInfo from './BranchesInfo'

class ApplicationInfo extends Component {

    constructor(...args) {
        super(...args);
        this.state = {
            info: {
                cust: {},
                build: {}
            },
            env: {
                systemProperties: {},
                systemEnvironment: {}
            },
            metrics: {}
        }
    }

    componentDidMount() {
        resource.query('/info').then(info => {
            this.setState({info})
        })
        resource.query('/metrics').then(metrics => {
            this.setState({metrics})
        })
        resource.query('/env').then(env => {
            this.setState({env})
        })
    }

    render() {
        const {t} = this.props
        return (
            <Tabs type="editable-card" forceRender={true} className="no-margin" hideAdd>
                <Tabs.TabPane key={"build"} tab={t("ui3.ApplicationInfo.attrs.build.caption", {ns: "classes"})}
                              closable={false}>
                    <Table showHeader={false} pagination={false} columns={[
                        {dataIndex: "key"},
                        {dataIndex: "value"},
                    ]} dataSource={[
                        {key: "Artifact", value: this.state.info.build.artifact},
                        {key: "Application Group", value: this.state.info.build.group},
                        {key: "Version", value: this.state.info.build.version},
                        {
                            key: "Build Time",
                            value: moment(this.state.info.build.time).format('dddd, DD MMMM YYYY, h:mm:ss')
                        },
                    ]}/>
                </Tabs.TabPane>
                <Tabs.TabPane key={"props"} tab={t("ui3.ApplicationInfo.attrs.props.caption", {ns: "classes"})}
                              closable={false}>
                    <Table showHeader={false} pagination={false} size="small" columns={[
                        {dataIndex: "key", width: "30%"},
                        {dataIndex: "value"},
                    ]} dataSource={
                        _.sortBy(Object.keys(this.state.env.systemProperties), key=>key.toLowerCase()).filter(key => key.toLowerCase().indexOf("password") === -1).map(key => {
                            return {key, value: this.state.env.systemProperties[key]}
                        })}/>
                </Tabs.TabPane>
                <Tabs.TabPane key={"env"} tab={t("ui3.ApplicationInfo.attrs.env.caption", {ns: "classes"})} closable={false}>
                    <Table showHeader={false} pagination={false} size="small" columns={[
                        {dataIndex: "key", width: "30%"},
                        {dataIndex: "value"},
                    ]} dataSource={
                        _.sortBy(Object.keys(this.state.env.systemEnvironment), key=>key.toLowerCase()).filter(key => key.toLowerCase().indexOf("password") === -1).map(key => {
                            return {key, value: this.state.env.systemEnvironment[key]}
                        })}/>
                </Tabs.TabPane>
                <Tabs.TabPane key={"metrics"} tab={t("ui3.ApplicationInfo.attrs.metrics.caption", {ns: "classes"})} closable={false}>
                    <Table showHeader={false} pagination={false} size="small" columns={[
                        {dataIndex: "key", width: "50%"},
                        {dataIndex: "value"},
                    ]} dataSource={
                        _.sortBy(Object.keys(this.state.metrics), key=>key.toLowerCase()).filter(key => key.toLowerCase().indexOf("password") === -1).map(key => {
                            return {key, value: this.state.metrics[key]}
                        })}/>
                </Tabs.TabPane>
                <Tabs.TabPane key={"branches"} tab={t("ui3.ApplicationInfo.attrs.branches.caption", {ns: "classes"})} closable={false}>
                    <BranchesInfo {...this.props}/>
                </Tabs.TabPane>
            </Tabs>
        )
    }

}

export default translate()(ApplicationInfo);
