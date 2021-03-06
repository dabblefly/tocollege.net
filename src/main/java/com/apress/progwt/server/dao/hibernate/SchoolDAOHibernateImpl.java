/*
 * Copyright 2008 Jeff Dwyer
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.apress.progwt.server.dao.hibernate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Expression;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projections;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.support.HibernateDaoSupport;

import com.apress.progwt.client.domain.Application;
import com.apress.progwt.client.domain.ForumPost;
import com.apress.progwt.client.domain.Loadable;
import com.apress.progwt.client.domain.ProcessType;
import com.apress.progwt.client.domain.RatingType;
import com.apress.progwt.client.domain.School;
import com.apress.progwt.client.domain.SchoolForumPost;
import com.apress.progwt.client.domain.User;
import com.apress.progwt.client.domain.UserForumPost;
import com.apress.progwt.client.domain.dto.PostsList;
import com.apress.progwt.server.dao.SchoolDAO;

public class SchoolDAOHibernateImpl extends HibernateDaoSupport implements
        SchoolDAO {

    private int autoCompleteMax = 7;

    private static final Logger log = Logger
            .getLogger(SchoolDAOHibernateImpl.class);

    public Loadable get(Class<? extends Loadable> loadable, Long id) {
        return (Loadable) getHibernateTemplate().get(loadable, id);
    }

    public List<School> getAllSchools(int start, int max) {
        DetachedCriteria crit = DetachedCriteria.forClass(School.class)
                .addOrder(Order.desc("popularityCounter"));

        // .add(
        // Expression.and(Expression.gt("id", 890l),
        // Expression.eq("latitude", -1d)));
        List<School> list = getHibernateTemplate().findByCriteria(crit,
                start, max);
        return list;
    }

    public List<ProcessType> getDefaultProcessTypes() {
        DetachedCriteria crit = DetachedCriteria.forClass(
                ProcessType.class).add(
                Expression.eq("useByDefault", true)).addOrder(
                Order.asc("id"));

        return getHibernateTemplate().findByCriteria(crit);
    }

    public List<RatingType> getDefaultRatingTypes() {
        DetachedCriteria crit = DetachedCriteria.forClass(
                RatingType.class)
                .add(Expression.eq("useByDefault", true)).addOrder(
                        Order.asc("id"));

        return getHibernateTemplate().findByCriteria(crit);
    }

    public ProcessType getProcessForName(String string) {
        DetachedCriteria crit = DetachedCriteria.forClass(
                ProcessType.class).add(Expression.eq("name", string));

        return (ProcessType) DataAccessUtils
                .uniqueResult(getHibernateTemplate().findByCriteria(crit));
    }

    public School getSchoolFromName(String name) {
        return (School) DataAccessUtils
                .uniqueResult(getHibernateTemplate().find(
                        "from School where name=?", name));
    }

    public List<School> getSchoolsMatching(String match) {
        return getSchoolsMatching(match, 0, autoCompleteMax,
                MatchMode.ANYWHERE);
    }

    /**
     * use start matchmode
     */
    public List<School> getSchoolsMatching(String match, int start,
            int max) {
        return getSchoolsMatching(match, start, max, MatchMode.START);
    }

    public List<School> getSchoolsMatching(String match, int start,
            int max, MatchMode matchMode) {

        DetachedCriteria crit = DetachedCriteria.forClass(School.class)
                .add(Expression.ilike("name", match, matchMode))
                .addOrder(Order.asc("name"));

        List<School> list = getHibernateTemplate().findByCriteria(crit,
                start, max);

        return list;
    }

    public List<ProcessType> matchProcessType(String queryString) {
        DetachedCriteria crit = DetachedCriteria.forClass(
                ProcessType.class)
                .add(
                        Expression.ilike("name", queryString,
                                MatchMode.ANYWHERE)).addOrder(
                        Order.asc("name"));

        List<ProcessType> list = getHibernateTemplate().findByCriteria(
                crit, 0, autoCompleteMax);

        return list;

    }

    public Loadable save(Loadable loadable) {
        getHibernateTemplate().saveOrUpdate(loadable);
        return loadable;
    }

    public void delete(Loadable loadable) {
        getHibernateTemplate().delete(loadable);
        getHibernateTemplate().flush();
    }

    /**
     * Change the number of autocomplete matches that are returned.
     * 
     * @param autoCompleteMax
     */
    public void setAutoCompleteMax(int autoCompleteMax) {
        this.autoCompleteMax = autoCompleteMax;
    }

    public int getAutoCompleteMax() {
        return autoCompleteMax;
    }

    /**
     * get the total number of rows without actually returning all rows
     * NOTE: important to set the start row here, otherwise when we start
     * paging, this criteria will be affected and we won't get the first
     * row.
     * 
     * @param criteria
     * @return
     */
    private int getRowCount(DetachedCriteria criteria) {
        criteria.setProjection(Projections.rowCount());
        return ((Integer) getHibernateTemplate().findByCriteria(criteria,
                0, 1).get(0)).intValue();
    }

    /**
     * 
     * Posts that are threads will have their threadPost field == null.
     * 
     * Could try to optimize by getting close to: SELECT f.id,
     * f.thread_id, count(s.id) FROM `forumposts` f left JOIN forumposts s
     * on f.id = s.thread_id WHERE f.thread_id IS NULL group by f.id
     * 
     * @param topicName
     * 
     */
    public PostsList getThreads(Class<? extends ForumPost> forumClass,
            String topicName, long topicID, int start, int max) {

        System.out.println("start " + start + " max " + max);

        DetachedCriteria crit = DetachedCriteria.forClass(forumClass,
                "fp").add(
                Expression.and(Expression.eq(topicName, topicID),
                        Expression.isNull("threadPost"))).addOrder(
                Order.desc("date"));

        // alternative
        // String query = "select post, count(rp) from " + forumClass
        // + " post left join post.replies as rp where post."
        // + topicName
        // + " = ? and post.threadPost is null group by post."
        // + topicName + " order by post.date desc ";
        // List<Object[]> res = finder(query, topicID);
        // List<ForumPost> posts = new ArrayList<ForumPost>(res.size());
        // for (Object[] objects : res) {
        // ForumPost fp = (ForumPost) objects[0];
        // long count = (Long) objects[1];
        // fp.setReplyCount((int) count);
        // posts.add(fp);
        // }

        List<ForumPost> posts = getHibernateTemplate().findByCriteria(
                crit, start, max);

        // set the reply count so we can have this information w/o
        // loading
        // the actual set.
        for (ForumPost fp : posts) {
            fp.setReplyCount(fp.getReplies().size());
        }

        PostsList rtn = new PostsList(posts, getRowCount(crit));

        return rtn;
    }

    private List finder(String hql, Object... args) {
        return getHibernateTemplate().find(hql, args);
    }

    public PostsList getPostsForThread(ForumPost post, int start, int max) {
        DetachedCriteria crit = DetachedCriteria
                .forClass(ForumPost.class).add(
                        Expression.or(Expression.eq("threadPost.id", post
                                .getId()), Expression.eq("id", post
                                .getId()))).addOrder(Order.asc("date"));

        List<ForumPost> posts = getHibernateTemplate().findByCriteria(
                crit, start, max);

        PostsList rtn = new PostsList(posts, getRowCount(crit));

        return rtn;
    }

    public PostsList getSchoolThreads(long schoolID, int start, int max) {
        return getThreads(SchoolForumPost.class, "school.id", schoolID,
                start, max);
    }

    public PostsList getUserThreads(long userID, int start, int max) {
        return getThreads(UserForumPost.class, "user.id", userID, start,
                max);
    }

    public PostsList getRecentForumPosts(int start, int max) {
        DetachedCriteria crit = DetachedCriteria.forClass(
                ForumPost.class, "fp").add(
                Expression.isNull("threadPost")).addOrder(
                Order.desc("date"));

        List<ForumPost> posts = getHibernateTemplate().findByCriteria(
                crit, start, max);

        PostsList rtn = new PostsList(posts, getRowCount(crit));

        return rtn;
    }

    /**
     * Batch Update in HQL
     * http://www.hibernate.org/hib_docs/reference/en/html/batch.html#batch-direct
     */
    public void incrementSchoolPopularity(final School school) {
        getHibernateTemplate().execute(new HibernateCallback() {
            public Object doInHibernate(Session session)
                    throws HibernateException, SQLException {

                String hql = "update School set popularityCounter = popularityCounter + 1 where id = :id";
                int updated = session.createQuery(hql).setLong("id",
                        school.getId()).executeUpdate();

                return updated;
            }
        });

    }

    public List<User> getUsersInterestedIn(School school) {
        return getUsersInterestedIn(school, 10);
    }

    public List<User> getUsersInterestedIn(School school, int max) {
        DetachedCriteria crit = DetachedCriteria.forClass(
                Application.class, "app").add(
                Expression.eq("school", school));

        List<Application> applications = getHibernateTemplate()
                .findByCriteria(crit, 0, max);

        List<User> users = new ArrayList<User>(applications.size());
        for (Application app : applications) {
            users.add(app.getUser());
        }
        return users;
    }
}
