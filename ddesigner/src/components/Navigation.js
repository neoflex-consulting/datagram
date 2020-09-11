import React, { Component } from 'react';
import {Row, Col, Menu, Breadcrumb, Avatar} from 'antd';
import PropTypes from 'prop-types';
import i18n from '../i18n.js';
import { translate } from 'react-i18next'
import { getIcon } from './../utils/meta'
import { createHrefWithNewObject } from './../utils/encode'
import { getBasename } from './../utils/meta'

class Navigation extends Component {
    static propTypes = {
        onSelectObject: PropTypes.func.isRequired,
        activeObject: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.state = { path: [] }
    }

    onRightMenu(e) {
        if (e.keyPath[e.keyPath.length - 1] === "history") {
            this.props.push(JSON.parse(e.key))
        }
        else if (e.keyPath.length > 1 && e.keyPath[1] === "branch") {
            this.props.context.setCurrentBranch(e.key)
        }
        else if (e.key === "ru") {
            this.props.setLang('ru-RU')
        }
        else if (e.key === "en") {
            this.props.setLang('en-EN')
        }
        else if (e.key === "login") {
            this.props.login(true)
        }
        else if (e.key === "logout") {
            this.props.resource.logout()
        }
        else if (e.key.indexOf("path=") === 0) {
            var path = this.props.path.find((m) => {
                return "path=" + m.name === e.key
            })
            this.props.onSelectObject(path)
        }
    }

    render() {
        const { t } = this.props
        const { location } = window
        return (
            <Row type="flex" justify="space-between">
                <Col>
                    <Breadcrumb mode="horizontal">
                        {this.props.path.map(m => {
                            return (
                                <Breadcrumb.Item key={"path=" + m.name}>
                                    <span onClick={() => this.props.onSelectObject(m)}>
                                        <Avatar src={getIcon(m)}
                                                size={"small"}/>&nbsp;{!m.hasOwnProperty('e_id') ? t((m.name) + '.caption', {ns: ['modules']}) : m.name}
                                    </span>
                                </Breadcrumb.Item>)
                        })}
                    </Breadcrumb>
                </Col>
                <Col>
                    <Menu selectable={false} mode="horizontal" onClick={(e) => this.onRightMenu(e)}
                          defaultSelectedKeys={['en-EN']}
                          style={{float: "right"}}>
                        <Menu.SubMenu key="history" style={{marginRight: "15px"}}
                                      title={<span><Avatar className="avatar-button-smallest"
                                                           src={"images/icon-core/link.svg"}/> {t("links")}</span>}>
                            {this.props.pathHistory.map(p =>
                                <Menu.Item key={JSON.stringify(p)}>
                                        <span><Avatar src={getIcon(p[p.length - 1])} size={"small"}
                                                      style={{marginRight: "10px"}}/>{p[p.length - 1].name}<a
                                            onClick={(e) => {
                                                e.stopPropagation()
                                            }} target="_blank"
                                            href={createHrefWithNewObject(location, p[p.length - 1])}> &nbsp;>>> </a></span>
                                </Menu.Item>)}
                            {this.props.pathHistory.length && <Menu.Divider/>}
                            {[{_type_: "ui3.ApplicationInfo", name: "ApplicationInfo"}].map(m => <Menu.Item
                                key={JSON.stringify([m])}>
                                    <span><Avatar src={getIcon(m)} size={"small"}
                                                  style={{marginRight: "10px"}}/>{t(m.name + '.caption', {ns: ["modules"]})}</span>
                            </Menu.Item>)}
                        </Menu.SubMenu>
                        <Menu.SubMenu id="user" key="user" title={<span><Avatar className="avatar-button-smallest"
                                                                                src={this.props.stompConnected ? "images/icon-core/user-log.svg" : "images/icon-core/user.svg"}/> {this.props.user}</span>}>
                            <Menu.SubMenu id="branch" key="branch" title={<span><Avatar className="avatar-button-smallest"
                                                                                        src="images/icon-core/category.svg"/>&nbsp;{t('branch')}</span>}>
                                {Object.keys(this.props.context.branchInfo.branches).map(branch=><Menu.Item
                                    key={branch}><span>{branch}&nbsp;{this.props.context.branchInfo.current === branch &&
                                <Avatar className="avatar-button-smallest"
                                        src="images/icon-core/check.svg"/>}</span></Menu.Item>)}

                            </Menu.SubMenu>
                            <Menu.SubMenu id="lang" title={<span><Avatar className="avatar-button-smallest"
                                                                         src="images/icon-core/world.svg"/>&nbsp;{t('lang')}</span>}>
                                <Menu.Item key="ru"><span>ru&nbsp;{i18n.language === ('ru-RU') &&
                                <Avatar className="avatar-button-smallest"
                                        src="images/icon-core/check.svg"/>}</span></Menu.Item>
                                <Menu.Item key="en"><span>en&nbsp;{i18n.language === ('en-EN') &&
                                <Avatar className="avatar-button-smallest"
                                        src="images/icon-core/check.svg"/>}</span></Menu.Item>
                            </Menu.SubMenu>
                            <Menu.SubMenu id="language" title={<span><Avatar className="avatar-button-smallest"
                                                                             src="images/icon-core/manual.svg"/>&nbsp;{t('help')}</span>}>
                                <Menu.Item key="ru">
                                    <a style={{display: "table-cell"}}
                                       href={`${getBasename(location.pathname)}manual/UserManualRU/UserManualRU.html`}
                                       target="_blank">
                                        ru&nbsp;{t('manual')}
                                    </a>
                                </Menu.Item>
                                <Menu.Item key="en">
                                    <a style={{display: "table-cell"}}
                                       href={`${getBasename(location.pathname)}manual/UserManualEN/UserManualEN.html`}
                                       target="_blank">
                                        en&nbsp;{t('manual')}
                                    </a>
                                </Menu.Item>
                            </Menu.SubMenu>
                            {!this.props.user && <Menu.Item key="login"><Avatar className="avatar-button-smallest"
                                                                                src="images/icon-core/sign-in.svg"/>&nbsp;{t('login')}
                            </Menu.Item>}
                            {this.props.user && <Menu.Item key="logout"><Avatar className="avatar-button-smallest"
                                                                                src="images/icon-core/sign-out.svg"/>&nbsp;{t('logout')}
                            </Menu.Item>}
                        </Menu.SubMenu>
                    </Menu>
                </Col>
            </Row>
        )
    }
}

export default translate()(Navigation);
